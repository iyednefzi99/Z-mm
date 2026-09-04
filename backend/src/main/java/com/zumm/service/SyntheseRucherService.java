package com.zumm.service;

import com.zumm.domain.EtatRuche;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.AlerteRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.IndiceColonie;
import com.zumm.web.dto.SyntheseRucher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregats au niveau du RUCHER (SPRINT-23, lot B).
 *
 * <p>Ferme la ligne « vision a trois niveaux (ruche → rucher → exploitation) »
 * du §7 de {@code docs/ECART-CONCURRENTS.md} : le niveau intermediaire manquait,
 * alors que c'est celui auquel on travaille. Personne ne se deplace pour une
 * ruche ni pour une exploitation — on va au rucher.
 *
 * <p><strong>Tout est lu en bloc, jamais par rucher.</strong> Six requetes au
 * total, quel que soit le nombre de ruchers : le N+1 serait ici particulierement
 * couteux, un tableau de bord affichant tous les ruchers a la fois.
 *
 * <p><strong>Ce que la synthese refuse de moyenner.</strong> L'indice de sante
 * moyen ne porte que sur les colonies REELLEMENT evaluees. Compter une colonie
 * non visitee comme un zero ferait chuter la moyenne d'un rucher qu'on n'a pas
 * encore vu, et compter comme 100 la ferait mentir dans l'autre sens. Le nombre
 * de colonies evaluees accompagne donc la moyenne, et un rucher sans aucune
 * observation rend {@code null}.
 */
@Service
@Transactional(readOnly = true)
public class SyntheseRucherService {

    /** Fenetre de production : la saison en cours, pas l'historique complet. */
    private static final int SAISON_JOURS = 365;

    private final SiteRepository sites;
    private final RucheRepository ruches;
    private final RecolteRepository recoltes;
    private final AlerteRepository alertes;
    private final TacheRepository taches;
    private final TraitementRepository traitements;
    private final IndiceColonieService indices;

    public SyntheseRucherService(SiteRepository sites, RucheRepository ruches,
            RecolteRepository recoltes, AlerteRepository alertes, TacheRepository taches,
            TraitementRepository traitements, IndiceColonieService indices) {
        this.sites = sites;
        this.ruches = ruches;
        this.recoltes = recoltes;
        this.alertes = alertes;
        this.taches = taches;
        this.traitements = traitements;
        this.indices = indices;
    }

    public SyntheseRucher pourSite(Long siteId) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> RessourceIntrouvable.de("Site", siteId));
        return construire(List.of(site)).get(0);
    }

    /**
     * Tous les ruchers, du plus preoccupant au plus calme.
     *
     * <p>L'ordre est celui du travail : ce qui a des colonies a soigner d'abord,
     * puis ce qui a des alertes, puis le reste. Trier par nom ferait chercher.
     */
    public List<SyntheseRucher> tous() {
        return construire(sites.findAll());
    }

    private List<SyntheseRucher> construire(List<Site> retenus) {
        LocalDate jour = LocalDate.now();
        LocalDate debutSaison = jour.minusDays(SAISON_JOURS);
        Set<Long> siteIds = retenus.stream().map(Site::getId).collect(Collectors.toSet());

        Map<Long, List<Ruche>> ruchesParSite = ruches.findAll().stream()
                .filter(r -> r.getSite() != null && siteIds.contains(r.getSite().getId()))
                .collect(Collectors.groupingBy(r -> r.getSite().getId()));

        // Indices : calcules une fois pour tout le parc, puis distribues. Les
        // recalculer par rucher relirait les memes visites autant de fois qu'il y
        // a de ruchers.
        Map<Long, IndiceColonie> indiceParRuche = indices.parc().stream()
                .collect(Collectors.toMap(IndiceColonie::rucheId, Function.identity()));

        Map<Long, Long> alertesParRuche = alertes.findByOuverteTrueOrderByOuverteLeDesc().stream()
                .filter(a -> a.getRuche() != null)
                .collect(Collectors.groupingBy(a -> a.getRuche().getId(), Collectors.counting()));

        Map<Long, Long> tachesParRuche = taches.findAll().stream()
                .filter(t -> !t.isFaite() && t.getRuche() != null)
                .collect(Collectors.groupingBy(t -> t.getRuche().getId(), Collectors.counting()));

        Set<Long> sousCarence = traitements.sousCarenceAu(jour).stream()
                .filter(t -> t.sousCarence(jour))
                .map(t -> t.getRuche().getId())
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> productionParRuche = recoltes.findByOrderByDateRecolteDescIdDesc()
                .stream()
                .filter(r -> !r.getDateRecolte().isBefore(debutSaison))
                .collect(Collectors.groupingBy(r -> r.getRuche().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Recolte::getQuantiteKg,
                                BigDecimal::add)));

        List<SyntheseRucher> syntheses = new ArrayList<>();
        for (Site site : retenus) {
            List<Ruche> duSite = ruchesParSite.getOrDefault(site.getId(), List.of());
            syntheses.add(synthese(site, duSite, indiceParRuche, alertesParRuche, tachesParRuche,
                    sousCarence, productionParRuche));
        }
        // Le plus preoccupant d'abord : colonies a soigner, puis alertes, puis
        // taille. Un tri alphabetique obligerait a chercher.
        syntheses.sort((a, b) -> {
            int parCarence = Integer.compare(b.ruchesSousCarence(), a.ruchesSousCarence());
            if (parCarence != 0) {
                return parCarence;
            }
            int parAlertes = Long.compare(b.alertesOuvertes(), a.alertesOuvertes());
            return parAlertes != 0 ? parAlertes : Integer.compare(b.nbRuches(), a.nbRuches());
        });
        return syntheses;
    }

    private SyntheseRucher synthese(Site site, List<Ruche> duSite,
            Map<Long, IndiceColonie> indiceParRuche, Map<Long, Long> alertesParRuche,
            Map<Long, Long> tachesParRuche, Set<Long> sousCarence,
            Map<Long, BigDecimal> productionParRuche) {

        List<IndiceColonie> evalues = duSite.stream()
                .map(r -> indiceParRuche.get(r.getId()))
                .filter(i -> i != null && i.composantes() > 0)
                .toList();

        // Moyenne sur les seules colonies evaluees : compter une colonie non
        // visitee comme 0 ferait chuter un rucher qu'on n'a pas encore vu, et
        // comme 100 le ferait mentir dans l'autre sens.
        Integer santeMoyenne = evalues.isEmpty() ? null
                : (int) Math.round(evalues.stream().mapToInt(IndiceColonie::sante).average()
                        .orElse(0));
        Integer risqueMax = evalues.isEmpty() ? null
                : evalues.stream().mapToInt(IndiceColonie::risqueEssaimage).max().orElse(0);

        BigDecimal production = duSite.stream()
                .map(r -> productionParRuche.getOrDefault(r.getId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);

        return new SyntheseRucher(
                site.getId(),
                site.getNom(),
                site.getVille(),
                site.getPriorite(),
                duSite.size(),
                (int) duSite.stream().filter(r -> r.getEtat() != EtatRuche.CLOTUREE).count(),
                santeMoyenne,
                evalues.size(),
                risqueMax,
                (int) duSite.stream().filter(r -> sousCarence.contains(r.getId())).count(),
                duSite.stream().mapToLong(r -> alertesParRuche.getOrDefault(r.getId(), 0L)).sum(),
                duSite.stream().mapToLong(r -> tachesParRuche.getOrDefault(r.getId(), 0L)).sum(),
                production);
    }
}
