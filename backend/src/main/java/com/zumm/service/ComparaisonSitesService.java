package com.zumm.service;

import com.zumm.domain.Recolte;
import com.zumm.domain.RessourceFlorale;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RessourceFloraleRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.ComparaisonSite;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compare des emplacements candidats (SPRINT-23, lot B).
 *
 * <p>Ferme le 🟡 du §2 : « {@code SiteController} compare les sites entre eux
 * dans l'ESPACE ; jamais sur leur environnement ou leur potentiel ». Les grappes
 * et le voisinage disent qui est proche de quoi ; ils ne disent pas ou une ruche
 * produit.
 *
 * <p><strong>Ce service ne classe pas.</strong> Il aligne des criteres
 * comparables et laisse l'apiculteur trancher. Une note unique — « ce rucher vaut
 * 78 sur 100 » — melangerait des grandeurs qui ne s'additionnent pas : des
 * kilos, des especes florales, une altitude et une densite de voisinage. Elle
 * aurait surtout l'autorite d'un chiffre sans en avoir la matiere.
 *
 * <p><strong>Ce qu'il ne rend pas : de position.</strong> Comparer trois
 * emplacements est exactement le moment ou l'on serait tente d'en donner les
 * coordonnees ; elles restent hors de cette reponse, qui n'en a pas besoin pour
 * comparer.
 */
@Service
@Transactional(readOnly = true)
public class ComparaisonSitesService {

    /** Historique de production retenu : deux saisons, de quoi lisser une annee creuse. */
    private static final int HISTORIQUE_JOURS = 730;

    /** Au-dela, deux ruchers ne se concurrencent plus pour le meme nectar. */
    private static final double VOISINAGE_METRES = 3000;

    private static final int MINIMUM_COMPARABLE = 2;

    private final SiteRepository sites;
    private final RucheRepository ruches;
    private final RecolteRepository recoltes;
    private final RessourceFloraleRepository ressources;

    public ComparaisonSitesService(SiteRepository sites, RucheRepository ruches,
            RecolteRepository recoltes, RessourceFloraleRepository ressources) {
        this.sites = sites;
        this.ruches = ruches;
        this.recoltes = recoltes;
        this.ressources = ressources;
    }

    /**
     * Compare les sites demandes, dans l'ordre demande.
     *
     * <p>L'ordre est conserve : c'est celui dans lequel l'apiculteur a pose la
     * question, et le reordonner par un critere quelconque reviendrait a repondre
     * a sa place.
     */
    public List<ComparaisonSite> comparer(List<Long> siteIds) {
        if (siteIds == null || siteIds.size() < MINIMUM_COMPARABLE) {
            throw new RequeteInvalide(
                    "Comparer demande au moins " + MINIMUM_COMPARABLE + " emplacements.");
        }
        Set<Long> demandes = new LinkedHashSet<>(siteIds);
        LocalDate depuis = LocalDate.now().minusDays(HISTORIQUE_JOURS);
        int mois = LocalDate.now().getMonthValue();

        Map<Long, List<Ruche>> parSite = ruches.findAll().stream()
                .filter(r -> r.getSite() != null)
                .collect(Collectors.groupingBy(r -> r.getSite().getId()));

        Map<Long, List<RessourceFlorale>> floreParSite = ressources.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getSite().getId()));

        Map<Long, BigDecimal> productionParRuche = recoltes.findByOrderByDateRecolteDescIdDesc()
                .stream()
                .filter(r -> !r.getDateRecolte().isBefore(depuis))
                .collect(Collectors.groupingBy(r -> r.getRuche().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Recolte::getQuantiteKg,
                                BigDecimal::add)));

        return demandes.stream()
                .map(id -> sites.findById(id).orElseThrow(() ->
                        new RequeteInvalide("Site inconnu dans ce tenant : " + id)))
                .map(site -> comparer(site, parSite, floreParSite, productionParRuche, mois))
                .toList();
    }

    private ComparaisonSite comparer(Site site, Map<Long, List<Ruche>> parSite,
            Map<Long, List<RessourceFlorale>> floreParSite,
            Map<Long, BigDecimal> productionParRuche, int mois) {

        List<Ruche> duSite = parSite.getOrDefault(site.getId(), List.of());
        List<RessourceFlorale> flore = floreParSite.getOrDefault(site.getId(), List.of());

        // Production PAR RUCHE et non totale : un rucher de vingt colonies
        // produit mecaniquement plus qu'un rucher de cinq, ce qui ne dit rien de
        // l'emplacement. C'est le rendement qui se compare.
        BigDecimal rendement = null;
        if (!duSite.isEmpty()) {
            BigDecimal total = duSite.stream()
                    .map(r -> productionParRuche.getOrDefault(r.getId(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            rendement = total.divide(BigDecimal.valueOf(duSite.size()), 2, RoundingMode.HALF_UP);
        }

        long enFleur = flore.stream().filter(r -> r.enFloraison(mois)).count();

        return new ComparaisonSite(
                site.getId(),
                site.getNom(),
                site.getVille(),
                site.getTypeSite(),
                site.getExposition(),
                site.getAltitude(),
                duSite.size(),
                rendement,
                flore.size(),
                (int) enFleur,
                // Densite de voisinage : combien de RUCHERS de l'exploitation se
                // partagent le meme nectar. Deux ruchers a huit cents metres l'un
                // de l'autre ne sont pas deux emplacements, c'est un seul.
                voisinsProches(site));
    }

    /**
     * Ruchers de l'exploitation a moins de trois kilometres.
     *
     * <p>Le calcul passe par PostGIS ({@code idsProches}), qui sert deja aux
     * grappes et au voisinage : refaire une distance en Java donnerait un
     * resultat different de celui de la carte, pour la meme question.
     */
    private int voisinsProches(Site site) {
        return (int) sites.idsProches(site.getLatitude().doubleValue(),
                        site.getLongitude().doubleValue(), VOISINAGE_METRES)
                .stream()
                .filter(id -> !id.equals(site.getId()))
                .count();
    }
}
