package com.zumm.service;

import com.zumm.domain.EmplacementSite;
import com.zumm.domain.Ferme;
import com.zumm.domain.RessourceFlorale;
import com.zumm.domain.Site;
import com.zumm.repository.EmplacementSiteRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.RessourceFloraleRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.DemenagementCorps;
import com.zumm.web.dto.EmplacementReponse;
import com.zumm.web.dto.GrappeSites;
import com.zumm.web.dto.RessourceFloraleCorps;
import com.zumm.web.dto.RessourceFloraleReponse;
import com.zumm.web.dto.SiteCorps;
import com.zumm.web.dto.SiteReponse;
import com.zumm.web.dto.VoisinSite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations metier sur les sites (US-003), geolocalises et rattaches a une ferme.
 *
 * <p>Applique les contraintes de composition (US-006) qui croisent plusieurs
 * champs — l'ordre des dates de cycle de vie — pour renvoyer un 400 clair. Les
 * bornes de coordonnees sont deja validees sur le DTO, et les contraintes
 * {@code CHECK} en base restent le garde-fou ultime. Les DTO sont construits dans
 * la transaction (la ferme, chargee paresseusement, y est accessible).
 */
@Service
@Transactional
public class SiteService {

    private final SiteRepository sites;
    private final FermeRepository fermes;
    private final RucheRepository ruches;
    private final RessourceFloraleRepository ressources;
    private final EmplacementSiteRepository emplacements;
    private final PolitiquePositions positions;

    public SiteService(SiteRepository sites, FermeRepository fermes, RucheRepository ruches,
            RessourceFloraleRepository ressources, EmplacementSiteRepository emplacements,
            PolitiquePositions positions) {
        this.sites = sites;
        this.fermes = fermes;
        this.ruches = ruches;
        this.ressources = ressources;
        this.emplacements = emplacements;
        this.positions = positions;
    }

    /**
     * Vue exposee d'un site : construction du DTO puis filtrage de la position
     * (SPRINT-12). Toute sortie de site passe par ici — c'est le seul point ou la
     * politique s'applique, donc le seul a auditer.
     */
    private SiteReponse vue(Site site) {
        return vue(site, ressourcesDe(site.getId()));
    }

    private SiteReponse vue(Site site, List<RessourceFloraleReponse> declarees) {
        return positions.masquer(SiteReponse.de(site, declarees));
    }

    private List<RessourceFloraleReponse> ressourcesDe(Long siteId) {
        return ressources.findBySite_IdOrderByRessourceAsc(siteId).stream()
                .map(RessourceFloraleReponse::de).toList();
    }

    public SiteReponse creer(SiteCorps corps) {
        verifierDates(corps);
        Site site = new Site(corps.nom(), fermeRequise(corps.fermeId()),
                corps.latitude(), corps.longitude(), corps.dateMiseEnOeuvre());
        appliquerOptionnels(site, corps);
        Site enregistre = sites.save(site);
        // Le premier emplacement s'ouvre avec le rucher : sans lui, l'historique
        // commencerait au premier demenagement et perdrait l'installation.
        emplacements.save(new EmplacementSite(enregistre, enregistre.getLatitude(),
                enregistre.getLongitude(), enregistre.getAltitude(),
                enregistre.getDateMiseEnOeuvre(), "installation"));
        return vue(enregistre, remplacerRessources(enregistre, corps.ressources()));
    }

    /**
     * Liste complete. Les ressources florales sont lues en UNE requete puis
     * regroupees : une lecture par site rejouerait le N+1 que le depot chasse
     * ailleurs (cf. les {@code @EntityGraph} des autres referentiels).
     */
    @Transactional(readOnly = true)
    public List<SiteReponse> lister() {
        Map<Long, List<RessourceFloraleReponse>> parSite = ressourcesParSite();
        return sites.findAll().stream()
                .map(site -> vue(site, parSite.getOrDefault(site.getId(), List.of())))
                .toList();
    }

    /** Page de la liste (US-052). Le total est porte par la Page, pas recompte. */
    @Transactional(readOnly = true)
    public Page<SiteReponse> lister(Pageable pagination) {
        Map<Long, List<RessourceFloraleReponse>> parSite = ressourcesParSite();
        return sites.findAll(pagination)
                .map(site -> vue(site, parSite.getOrDefault(site.getId(), List.of())));
    }

    private Map<Long, List<RessourceFloraleReponse>> ressourcesParSite() {
        return ressources.findAll().stream().collect(Collectors.groupingBy(
                ressource -> ressource.getSite().getId(),
                Collectors.mapping(RessourceFloraleReponse::de, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public SiteReponse obtenir(Long id) {
        return vue(entite(id));
    }

    public SiteReponse mettreAJour(Long id, SiteCorps corps) {
        verifierDates(corps);
        Site site = entite(id);
        site.setNom(corps.nom());
        site.setFerme(fermeRequise(corps.fermeId()));
        site.setLatitude(corps.latitude());
        site.setLongitude(corps.longitude());
        appliquerOptionnels(site, corps);
        return vue(site, remplacerRessources(site, corps.ressources()));
    }

    /**
     * Historique des emplacements occupes par un rucher (SPRINT-21, transhumance).
     *
     * <p>Le masque s'y applique comme sur la position courante, et pour une raison
     * plus forte : la suite des emplacements dit aussi ou le rucher se trouvait
     * quand personne ne le surveillait.
     */
    @Transactional(readOnly = true)
    public List<EmplacementReponse> historique(Long id) {
        entite(id);
        return emplacements.findBySite_IdOrderByDateDebutDescIdDesc(id).stream()
                .map(this::vue)
                .toList();
    }

    private EmplacementReponse vue(EmplacementSite emplacement) {
        EmplacementReponse brut = EmplacementReponse.de(emplacement);
        BigDecimal[] masquee = positions.masquer(brut.latitude(), brut.longitude());
        boolean exact = positions.positionExacteAutorisee();
        return new EmplacementReponse(brut.id(), brut.siteId(), masquee[0], masquee[1],
                exact ? brut.altitude() : null, brut.dateDebut(), brut.dateFin(),
                brut.motif(), brut.note(), brut.courant());
    }

    /**
     * Deplace un rucher : clot l'emplacement courant et en ouvre un nouveau
     * (SPRINT-21).
     *
     * <p>Operation distincte de la mise a jour, deliberement. Corriger une
     * position mal saisie et deplacer un rucher touchent aux memes colonnes mais
     * ne disent pas la meme chose ; seule la seconde doit laisser une trace, sans
     * quoi l'historique se remplirait de fausses transhumances a chaque faute de
     * frappe corrigee.
     */
    public SiteReponse demenager(Long id, DemenagementCorps corps) {
        Site site = entite(id);
        EmplacementSite courant = emplacements.findBySite_IdAndDateFinIsNull(id).orElse(null);
        if (courant != null) {
            if (corps.dateDebut().isBefore(courant.getDateDebut())) {
                throw new RequeteInvalide("Le nouvel emplacement (" + corps.dateDebut()
                        + ") commence avant l'emplacement courant (" + courant.getDateDebut()
                        + ").");
            }
            // La periode precedente se ferme le jour ou la suivante s'ouvre : un
            // rucher n'est jamais a deux endroits, ni nulle part entre les deux.
            courant.setDateFin(corps.dateDebut());
            // Le flush est OBLIGATOIRE ici, et ce n'est pas une precaution.
            // Hibernate ordonne ses actions par type : tous les INSERT d'abord,
            // les UPDATE ensuite. Sans ce flush, le nouvel emplacement (date_fin
            // nulle) serait insere AVANT que l'ancien ne soit clos, et l'index
            // unique partiel `uq_emplacement_courant` refuserait deux
            // emplacements ouverts sur le meme site — un demenagement echouerait
            // systematiquement, en 500.
            emplacements.flush();
        }
        EmplacementSite nouveau = new EmplacementSite(site, corps.latitude(), corps.longitude(),
                corps.altitude(), corps.dateDebut(),
                corps.motif() == null ? "transhumance" : corps.motif());
        nouveau.setNote(corps.note());
        emplacements.save(nouveau);

        site.setLatitude(corps.latitude());
        site.setLongitude(corps.longitude());
        site.setAltitude(corps.altitude());
        site.setDateDemenagement(corps.dateDebut());
        return vue(site);
    }

    /**
     * Remplace en bloc les ressources declarees d'un site.
     *
     * <p>Meme parti que la composition d'une ruche : la liste recue est la liste
     * finale. Une synchronisation ligne a ligne obligerait le client a suivre des
     * identifiants qu'il n'a aucune raison de connaitre.
     */
    private List<RessourceFloraleReponse> remplacerRessources(
            Site site, List<RessourceFloraleCorps> declarees) {
        if (declarees == null) {
            return ressourcesDe(site.getId());
        }
        ressources.deleteBySite_Id(site.getId());
        // Le vidage precede l'insertion : la contrainte uq_ressource_site refuse
        // deux fois la meme ressource sur un site, y compris entre l'ancienne
        // liste et la nouvelle.
        ressources.flush();
        List<RessourceFloraleReponse> vues = new ArrayList<>();
        for (RessourceFloraleCorps declaree : declarees) {
            RessourceFlorale ressource = new RessourceFlorale(
                    declaree.ressource(), declaree.distanceM(), declaree.note());
            ressource.setSite(site);
            // Les deux mois vont ensemble : une floraison qui commence sans finir
            // ne se lit pas. La base ne l'accepterait pas davantage
            // (`ck_ressource_mois`), mais le dire ici evite l'erreur SQL en 500.
            if (declaree.moisDebut() != null && declaree.moisFin() != null) {
                ressource.setMoisDebut(declaree.moisDebut());
                ressource.setMoisFin(declaree.moisFin());
            } else if (declaree.moisDebut() != null || declaree.moisFin() != null) {
                throw new RequeteInvalide(
                        "Une periode de floraison demande son debut ET sa fin, ou aucun des deux.");
            }
            vues.add(RessourceFloraleReponse.de(ressources.save(ressource)));
        }
        return vues;
    }

    public void supprimer(Long id) {
        sites.delete(entite(id));
    }

    /** Sites du tenant a moins de {@code rayonMetres} d'un point (US-003, PostGIS). */
    @Transactional(readOnly = true)
    public List<SiteReponse> proches(double latitude, double longitude, double rayonMetres) {
        return sites.findAllById(sites.idsProches(latitude, longitude, rayonMetres))
                .stream().map(this::vue).toList();
    }

    /**
     * Regroupe les sites du tenant par proximite geographique (US-045).
     *
     * <p>Le calcul est fait en base par {@code ST_ClusterDBSCAN}. Deux precautions :
     *
     * <ul>
     *   <li>le rayon est donne en metres reels, mais DBSCAN travaille sur la
     *       projection Web Mercator, qui dilate les distances d'un facteur
     *       {@code 1/cos(latitude)} — d'ou la calibration sur la latitude moyenne ;
     *   <li>DBSCAN classe en « bruit » les sites qui n'atteignent pas
     *       {@code minimumSites} voisins. Les perdre serait un contresens metier : ils
     *       ressortent en grappes d'un seul membre.
     * </ul>
     *
     * <p>Les grappes sont numerotees par taille decroissante, a egalite par plus petit
     * identifiant de site — l'ordre ne depend donc pas de celui rendu par la base.
     */
    @Transactional(readOnly = true)
    public List<GrappeSites> grappes(double distanceMetres, int minimumSites) {
        BigDecimal latitudeMoyenne = sites.latitudeMoyenne();
        if (latitudeMoyenne == null) {
            return List.of();
        }
        double eps = distanceMetres / Math.cos(Math.toRadians(latitudeMoyenne.doubleValue()));

        // Cle de groupe : le numero DBSCAN pour les sites agreges, l'identifiant du
        // site (negatif, pour ne pas collisionner) pour les isoles.
        Map<Long, List<Long>> parGroupe = new LinkedHashMap<>();
        for (SiteRepository.AffectationGrappe ligne : sites.affectationsGrappes(eps, minimumSites)) {
            Long cle = ligne.getGrappe() == null ? -ligne.getSiteId() : ligne.getGrappe().longValue();
            parGroupe.computeIfAbsent(cle, c -> new ArrayList<>()).add(ligne.getSiteId());
        }

        Map<Long, SiteReponse> parId = sites.findAllById(
                        parGroupe.values().stream().flatMap(List::stream).toList())
                .stream().collect(Collectors.toMap(Site::getId, SiteReponse::de));
        Map<Long, Long> ruchesParSite = ruches.comptesParSite().stream()
                .collect(Collectors.toMap(l -> (Long) l[0], l -> (Long) l[1]));

        List<List<SiteReponse>> groupes = parGroupe.values().stream()
                .map(ids -> ids.stream().map(parId::get).filter(Objects::nonNull)
                        .sorted(Comparator.comparing(SiteReponse::id)).toList())
                .filter(membres -> !membres.isEmpty())
                .sorted(Comparator.comparingInt((List<SiteReponse> m) -> m.size()).reversed()
                        .thenComparing(m -> m.get(0).id()))
                .toList();

        List<GrappeSites> grappes = new ArrayList<>();
        for (int i = 0; i < groupes.size(); i++) {
            grappes.add(enGrappe(i + 1, groupes.get(i), ruchesParSite));
        }
        return grappes;
    }

    /** Les {@code limite} sites du tenant les plus proches de {@code id} (US-046). */
    @Transactional(readOnly = true)
    public List<VoisinSite> voisins(Long id, int limite) {
        if (limite < 1) {
            throw new RequeteInvalide("La limite de voisins doit valoir au moins 1.");
        }
        Site reference = entite(id);
        List<SiteRepository.VoisinProche> lignes = sites.voisins(
                reference.getLatitude().doubleValue(),
                reference.getLongitude().doubleValue(),
                id,
                limite);
        Map<Long, SiteReponse> parId = sites.findAllById(
                        lignes.stream().map(SiteRepository.VoisinProche::getSiteId).toList())
                .stream().collect(Collectors.toMap(Site::getId, SiteReponse::de));
        // Une distance au decimetre depuis un site connu se trilatere : elle
        // reconstituerait la position que le masque vient d'arrondir. Elle est donc
        // degradee a la centaine de metres pour les profils non proprietaires.
        boolean exact = positions.positionExacteAutorisee();
        // L'ordre vient de la base (parcours d'index KNN) : on le conserve.
        return lignes.stream()
                .filter(ligne -> parId.containsKey(ligne.getSiteId()))
                .map(ligne -> new VoisinSite(
                        positions.masquer(parId.get(ligne.getSiteId())),
                        distanceExposee(ligne.getDistanceMetres(), exact)))
                .toList();
    }

    private static BigDecimal distanceExposee(double metres, boolean exact) {
        return exact
                ? BigDecimal.valueOf(metres).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(Math.round(metres / 100.0) * 100L).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * Le centroide est calcule sur les positions EXACTES puis masque, et non sur des
     * positions deja arrondies : arrondir avant de moyenner accumulerait les erreurs
     * d'arrondi au lieu de les compenser.
     */
    private GrappeSites enGrappe(int numero, List<SiteReponse> membres, Map<Long, Long> ruchesParSite) {
        BigDecimal nombre = BigDecimal.valueOf(membres.size());
        BigDecimal latitude = membres.stream().map(SiteReponse::latitude)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(nombre, 6, RoundingMode.HALF_UP);
        BigDecimal longitude = membres.stream().map(SiteReponse::longitude)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(nombre, 6, RoundingMode.HALF_UP);
        BigDecimal[] centroide = positions.masquer(latitude, longitude);
        long ruchesCumulees = membres.stream()
                .mapToLong(site -> ruchesParSite.getOrDefault(site.id(), 0L)).sum();
        return new GrappeSites(numero, centroide[0], centroide[1], membres.size(), ruchesCumulees,
                membres.stream().map(positions::masquer).toList());
    }

    Site entite(Long id) {
        return sites.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Site", id));
    }

    private void appliquerOptionnels(Site site, SiteCorps corps) {
        site.setAltitude(corps.altitude());
        site.setRayonButinageKm(corps.rayonButinageKm());
        site.setDateDemenagement(corps.dateDemenagement());
        site.setDateCloture(corps.dateCloture());
        site.setAdresseRue(vide(corps.adresseRue()));
        site.setCodePostal(vide(corps.codePostal()));
        site.setVille(vide(corps.ville()));
        site.setPays(vide(corps.pays()));
        site.setTypeSite(vide(corps.typeSite()));
        site.setExposition(vide(corps.exposition()));
        // Priorite absente = `normale`, jamais « la plus haute par prudence » :
        // un parc ou tout est strategique ne priorise rien.
        // La couverture reseau, elle, s'ECRASE avec le nul : « je ne sais plus »
        // est une reponse valable, la ou une priorite absente veut dire « comme
        // avant ». Les deux champs n'ont pas la meme semantique du vide.
        site.setCouvertureReseau(corps.couvertureReseau());
        if (corps.priorite() != null) {
            site.setPriorite(corps.priorite());
        }
    }

    /**
     * Une chaine vide n'est pas une valeur : c'est un champ de formulaire qu'on a
     * ouvert puis quitte. La stocker ferait echouer les CHECK du referentiel
     * ({@code ck_site_type}, {@code ck_site_pays}) sur une saisie que
     * l'utilisateur considere comme vierge.
     */
    private static String vide(String valeur) {
        return valeur == null || valeur.isBlank() ? null : valeur.trim();
    }

    /** US-006 : demenagement et cloture ne peuvent preceder la mise en oeuvre. */
    private void verifierDates(SiteCorps corps) {
        LocalDate debut = corps.dateMiseEnOeuvre();
        if (corps.dateDemenagement() != null && corps.dateDemenagement().isBefore(debut)) {
            throw new RequeteInvalide("La date de demenagement precede la mise en oeuvre.");
        }
        if (corps.dateCloture() != null && corps.dateCloture().isBefore(debut)) {
            throw new RequeteInvalide("La date de cloture precede la mise en oeuvre.");
        }
    }

    private Ferme fermeRequise(Long fermeId) {
        return fermes.findById(fermeId).orElseThrow(() ->
                new RequeteInvalide("Ferme inconnue dans ce tenant : " + fermeId));
    }
}
