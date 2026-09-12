package com.zumm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.Site;
import com.zumm.repository.CouvertSolRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.ConstatCouvertCorps;
import com.zumm.web.dto.CouvertRucher;
import com.zumm.web.dto.FiabiliteCouvert;
import com.zumm.web.dto.ParcelleCouvert;
import com.zumm.web.dto.SurfaceCouvert;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occupation du sol autour d'un rucher (SPRINT-32, lot H).
 *
 * <p>Ferme quatre lignes du §2 et une du §13. C'est le domaine de BeeGIS, le
 * seul concurrent a jouer sur le terrain que Zumm revendique.
 *
 * <p><strong>La donnee est accueillie, jamais interrogee</strong>
 * ([ADR-015]). Demander a un service tiers ce qu'il y a autour d'un rucher lui
 * apprendrait ou sont les ruches — ce que {@code PolitiquePositions} protege
 * depuis le SPRINT-12 et ce que le mode local du SPRINT-30 vient de couper pour
 * les tuiles de carte. L'exploitant verse un extrait dans SON PostGIS, et tout
 * le calcul se fait chez lui.
 *
 * <p><strong>La taxonomie est fermee</strong>, comme le referentiel du
 * SPRINT-28 : chaque source nomme ses classes autrement, et les laisser entrer
 * telles quelles rendrait deux exploitations incomparables. L'ingesteur traduit ;
 * il n'invente pas, et refuse ce qu'il ne sait pas traduire.
 */
@Service
public class CouvertSolService {

    /** Taxonomie fermee, identique a {@code ck_couvert_classe} (V31). */
    private static final Set<String> CLASSES = Set.of(
            "culture", "prairie", "foret", "lande", "verger", "vigne",
            "eau", "urbain", "sol_nu", "autre");

    /**
     * Plafond d'un versement.
     *
     * <p>Une couche departementale compte des centaines de milliers de
     * polygones ; les faire passer par une requete HTTP unique demanderait de
     * les tenir tous en memoire. Au-dela, le versement se decoupe — et le
     * decouper est le travail de celui qui exporte, qui sait ce qu'il exporte.
     */
    private static final int POLYGONES_MAX = 20_000;

    private final CouvertSolRepository couverts;
    private final SiteRepository sites;
    private final ConfigurationMetier configuration;
    private final ObjectMapper json;

    public CouvertSolService(CouvertSolRepository couverts, SiteRepository sites,
            ConfigurationMetier configuration, ObjectMapper json) {
        this.couverts = couverts;
        this.sites = sites;
        this.configuration = configuration;
        this.json = json;
    }

    /**
     * Verse une collection GeoJSON dans la couche.
     *
     * <p>Chaque entite doit porter une propriete {@code classe} appartenant a la
     * taxonomie. Une entite sans classe, ou avec une classe inconnue, fait
     * echouer le versement ENTIER : accepter les autres laisserait une couche
     * partielle dont personne ne saurait ce qu'elle omet, et les surfaces
     * calculees dessus seraient fausses sans le dire.
     *
     * @return le nombre de polygones verses
     */
    @Transactional
    public int verser(String source, int millesime, JsonNode collection) {
        if (source == null || source.isBlank()) {
            throw new RequeteInvalide(
                    "La source est obligatoire : un pourcentage sans sa provenance n'engage "
                            + "personne.");
        }
        JsonNode entites = collection.path("features");
        if (!entites.isArray() || entites.isEmpty()) {
            throw new RequeteInvalide("GeoJSON attendu : une FeatureCollection non vide.");
        }
        if (entites.size() > POLYGONES_MAX) {
            throw new RequeteInvalide("Versement limite a " + POLYGONES_MAX
                    + " polygones : decouper l'export.");
        }

        // Le millesime se REMPLACE : verser deux fois le meme sans purger
        // doublerait toutes les surfaces, et le total depasserait celui du
        // cercle sans que rien ne le signale.
        couverts.purger(millesime);

        int verses = 0;
        for (JsonNode entite : entites) {
            String classe = entite.path("properties").path("classe").asText(null);
            if (classe == null || !CLASSES.contains(classe)) {
                throw new RequeteInvalide(
                        "Classe de couvert inconnue : « " + classe + " ». L'ingesteur traduit "
                                + "vers la taxonomie fermee, il ne l'etend pas.");
            }
            JsonNode geometrie = entite.path("geometry");
            if (geometrie.isMissingNode() || geometrie.isNull()) {
                throw new RequeteInvalide("Une entite sans geometrie ne decrit aucun sol.");
            }
            couverts.inserer(classe, source, millesime, geometrie.toString());
            verses++;
        }
        return verses;
    }

    /**
     * Ce qu'il y a autour d'un rucher, au millesime demande.
     *
     * <p>Sans millesime, le plus recent : c'est la lecture attendue, et forcer
     * l'appelant a le choisir ferait poser une question dont il n'a pas encore
     * la reponse.
     */
    @Transactional(readOnly = true)
    public CouvertRucher autour(Long siteId, Integer millesimeDemande) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> RessourceIntrouvable.de("Site", siteId));
        List<Integer> disponibles = couverts.millesimes();
        Integer millesime = millesimeDemande != null ? millesimeDemande
                : disponibles.isEmpty() ? null : disponibles.get(0);

        BigDecimal rayonKm = site.getRayonButinageKm() != null
                ? site.getRayonButinageKm()
                : BigDecimal.valueOf(configuration.seuils().rayonButinageKm());
        BigDecimal surfaceCercle = surfaceCercleHa(rayonKm);

        if (millesime == null) {
            // Aucune couche versee : on le dit, plutot que de rendre des
            // surfaces nulles qui se liraient comme un environnement vide.
            return new CouvertRucher(site.getId(), site.getNom(), rayonKm, null, null,
                    surfaceCercle, null, null, List.of());
        }

        List<SurfaceCouvert> brutes =
                couverts.surfacesAutour(siteId, rayonKm.doubleValue() * 1000, millesime);
        BigDecimal totalDecrit = brutes.stream()
                .map(SurfaceCouvert::surfaceHa)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SurfaceCouvert> avecParts = brutes.stream()
                .map(s -> s.avecPart(part(s.surfaceHa(), surfaceCercle)))
                .toList();

        return new CouvertRucher(site.getId(), site.getNom(), rayonKm, millesime,
                couverts.source(millesime), surfaceCercle,
                part(totalDecrit, surfaceCercle),
                couverts.distanceCultureLaPlusProche(siteId, millesime),
                avecParts);
    }

    /**
     * Les memes surfaces, millesime par millesime.
     *
     * <p>Ferme la ligne « historique et rotation des cultures » : la rotation ne
     * se DEDUIT pas d'une seule couche, elle se LIT en comparant deux annees.
     * C'est pour cela que le millesime est obligatoire depuis la premiere ligne
     * versee.
     */
    @Transactional(readOnly = true)
    public List<CouvertRucher> rotation(Long siteId) {
        return couverts.millesimes().stream().map(m -> autour(siteId, m)).toList();
    }

    /** Millesimes disponibles, du plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<Integer> millesimes() {
        return couverts.millesimes();
    }

    // ─── Verification terrain (SPRINT-33, lot K) ────────────────────────────

    /**
     * Parcelles de la couche, ou celles d'un rucher.
     *
     * <p>{@code enAttente} restreint a ce que le terrain doit trancher. Sans
     * cette borne, l'ecran afficherait la couche entiere — des milliers de
     * polygones dont personne ne doute.
     */
    @Transactional(readOnly = true)
    public List<ParcelleCouvert> parcelles(Long siteId, boolean enAttente) {
        double rayonMetres = 0;
        if (siteId != null) {
            Site site = sites.findById(siteId)
                    .orElseThrow(() -> RessourceIntrouvable.de("Site", siteId));
            BigDecimal rayonKm = site.getRayonButinageKm() != null
                    ? site.getRayonButinageKm()
                    : BigDecimal.valueOf(configuration.seuils().rayonButinageKm());
            rayonMetres = rayonKm.doubleValue() * 1000;
        }
        return couverts.parcelles(siteId, rayonMetres, enAttente);
    }

    /**
     * Pose ou leve le doute sur une parcelle.
     *
     * <p>C'est un geste HUMAIN, et il le reste. Deduire le doute — « toute
     * culture d'un millesime de plus de deux ans » — fabriquerait une tournee de
     * verification que personne n'a demandee, sur des parcelles que personne ne
     * soupconne.
     */
    @Transactional
    public void marquer(Long id, boolean aConfirmer) {
        if (couverts.marquerAConfirmer(id, aConfirmer) == 0) {
            throw RessourceIntrouvable.de("Parcelle", id);
        }
    }

    /**
     * Enregistre ce que le terrain a montre.
     *
     * <p><strong>La classe de la source n'est jamais ecrasee.</strong> C'est la
     * decision de ce lot, et elle porte tout le ground truthing : ecrasee, la
     * parcelle raconterait que la couche avait raison depuis le debut, et
     * personne ne pourrait plus dire de quel millesime se defier. Le constat
     * s'ecrit a cote et prend la main sur les lectures — meme construction que
     * la floraison declaree de la `V21` et la floraison observee de la `V31`.
     *
     * <p>Le constat ne peut pas etre ANTERIEUR au millesime : constater en 2024
     * ce qu'une couche decrit pour 2026 ne verifie rien.
     */
    @Transactional
    public void constater(Long id, ConstatCouvertCorps corps) {
        if (corps.constateLe().isAfter(java.time.LocalDate.now())) {
            throw new RequeteInvalide(
                    "Un constat porte sur une visite deja faite, pas sur une visite prevue.");
        }
        ParcelleCouvert parcelle = couverts.parcelles(null, 0, false).stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> RessourceIntrouvable.de("Parcelle", id));
        if (corps.constateLe().getYear() < parcelle.millesime()) {
            throw new RequeteInvalide(
                    "Constat de " + corps.constateLe().getYear() + " sur une couche de "
                            + parcelle.millesime() + " : il ne verifie rien.");
        }
        couverts.enregistrerConstat(id, corps.classeConstatee(), corps.constateLe(),
                corps.note());
    }

    /**
     * Fiabilite mesuree d'un millesime.
     *
     * <p>La seule statistique que le ground truthing produise vraiment : non pas
     * « la couche est bonne », mais « sur les parcelles verifiees, elle s'est
     * trompee tant de fois ». Rendue en TROIS nombres et non en pourcentage
     * unique — un taux de 100 % sur deux parcelles verifiees ne dit rien, et
     * l'afficher seul le laisserait croire.
     */
    @Transactional(readOnly = true)
    public FiabiliteCouvert fiabilite(int millesime) {
        List<ParcelleCouvert> toutes = couverts.parcelles(null, 0, false).stream()
                .filter(p -> p.millesime() == millesime)
                .toList();
        long verifiees = toutes.stream().filter(p -> p.classeConstatee() != null).count();
        long dementies = toutes.stream().filter(ParcelleCouvert::dement).count();
        long enAttente = toutes.stream()
                .filter(p -> p.aConfirmer() && p.classeConstatee() == null)
                .count();
        return new FiabiliteCouvert(millesime, toutes.size(), (int) verifiees,
                (int) dementies, (int) enAttente);
    }

    @Transactional
    public int purger(int millesime) {
        return couverts.purger(millesime);
    }

    /** Surface d'un disque geodesique, en hectares. */
    private static BigDecimal surfaceCercleHa(BigDecimal rayonKm) {
        double rayonM = rayonKm.doubleValue() * 1000;
        return BigDecimal.valueOf(Math.PI * rayonM * rayonM / 10_000)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal part(BigDecimal surface, BigDecimal total) {
        if (surface == null || total.signum() == 0) {
            return null;
        }
        return surface.multiply(BigDecimal.valueOf(100))
                .divide(total, 1, RoundingMode.HALF_UP);
    }
}
