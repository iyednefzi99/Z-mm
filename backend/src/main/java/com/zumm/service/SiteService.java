package com.zumm.service;

import com.zumm.domain.Ferme;
import com.zumm.domain.Site;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.GrappeSites;
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
    private final PolitiquePositions positions;

    public SiteService(SiteRepository sites, FermeRepository fermes, RucheRepository ruches,
            PolitiquePositions positions) {
        this.sites = sites;
        this.fermes = fermes;
        this.ruches = ruches;
        this.positions = positions;
    }

    /**
     * Vue exposee d'un site : construction du DTO puis filtrage de la position
     * (SPRINT-12). Toute sortie de site passe par ici — c'est le seul point ou la
     * politique s'applique, donc le seul a auditer.
     */
    private SiteReponse vue(Site site) {
        return positions.masquer(SiteReponse.de(site));
    }

    public SiteReponse creer(SiteCorps corps) {
        verifierDates(corps);
        Site site = new Site(corps.nom(), fermeRequise(corps.fermeId()),
                corps.latitude(), corps.longitude(), corps.dateMiseEnOeuvre());
        appliquerOptionnels(site, corps);
        return vue(sites.save(site));
    }

    @Transactional(readOnly = true)
    public List<SiteReponse> lister() {
        return sites.findAll().stream().map(this::vue).toList();
    }

    /** Page de la liste (US-052). Le total est porte par la Page, pas recompte. */
    @Transactional(readOnly = true)
    public Page<SiteReponse> lister(Pageable pagination) {
        return sites.findAll(pagination).map(this::vue);
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
        return vue(site);
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
        site.setDateDemenagement(corps.dateDemenagement());
        site.setDateCloture(corps.dateCloture());
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
