package com.zumm.service;

import com.zumm.domain.Recolte;
import com.zumm.repository.RecolteRepository;
import com.zumm.web.dto.ComparaisonSaisons;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comparaison saison contre saison (SPRINT-27, lot E).
 *
 * <p>Ferme deux lignes qui n'en font qu'une : « comparaison des récoltes année
 * par année » (§6) et « servez-vous de l'historique de rotation sur 3 à 5 ans »
 * (§13, conseil de BeeGIS). Tous les agrégats existants portent sur une
 * <strong>période glissante</strong> — douze mois qui reculent chaque jour —, ce
 * qui ne permet jamais de dire « 2026 a mieux donné que 2025 ».
 *
 * <p><strong>L'année civile, et non une saison apicole à cheval.</strong> Une
 * saison qui irait de mars à février serait plus juste au nord et fausse au sud ;
 * surtout, elle rendrait toute comparaison ambiguë — « 2025 » désignerait deux
 * périodes différentes selon qui parle. L'année civile n'a pas ce défaut, et la
 * saison du nord la suit d'assez près.
 *
 * <p><strong>Le rendement compte les ruches qui ont PRODUIT</strong>, pas celles
 * qui existaient. Une exploitation qui double son cheptel double sa production
 * sans rien améliorer : c'est le rendement par ruche productive qui dit si la
 * saison fut bonne. Une année sans récolte rend un rendement {@code null} —
 * jamais zéro, qui ferait croire à une saison catastrophique là où il n'y a
 * simplement rien eu de saisi.
 */
@Service
@Transactional(readOnly = true)
public class ComparaisonSaisonsService {

    private final RecolteRepository recoltes;

    public ComparaisonSaisonsService(RecolteRepository recoltes) {
        this.recoltes = recoltes;
    }

    /**
     * Les saisons enregistrées, de la plus récente à la plus ancienne.
     *
     * <p>Seules les années où quelque chose a été récolté figurent : inventer
     * une ligne à zéro pour une année sans donnée ferait passer une absence de
     * saisie pour une absence de production.
     */
    public List<ComparaisonSaisons> saisons() {
        Map<Integer, List<Recolte>> parAnnee = recoltes.findByOrderByDateRecolteDescIdDesc()
                .stream()
                .collect(Collectors.groupingBy(r -> r.getDateRecolte().getYear()));

        return parAnnee.entrySet().stream()
                .map(entree -> saison(entree.getKey(), entree.getValue()))
                .sorted(Comparator.comparingInt(ComparaisonSaisons::annee).reversed())
                .toList();
    }

    private ComparaisonSaisons saison(int annee, List<Recolte> recoltesDeLAnnee) {
        List<Recolte> miel = recoltesDeLAnnee.stream().filter(Recolte::estDuMiel).toList();

        BigDecimal production = miel.stream()
                .map(Recolte::getQuantiteKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);

        Set<Long> productives = miel.stream()
                .filter(r -> r.getQuantiteKg().signum() > 0)
                .map(r -> r.getRuche().getId())
                .collect(Collectors.toSet());

        BigDecimal rendement = productives.isEmpty() ? null
                : production.divide(BigDecimal.valueOf(productives.size()), 2,
                        RoundingMode.HALF_UP);

        // Les autres produits ne se mélangent pas au miel : ils ont leurs
        // propres unités, et un total commun n'aurait aucun sens.
        List<ComparaisonSaisons.ProduitSaison> parProduit = recoltesDeLAnnee.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getTypeProduit() + "|" + r.getUnite(),
                        Collectors.reducing(BigDecimal.ZERO, Recolte::getQuantiteKg,
                                BigDecimal::add)))
                .entrySet().stream()
                .map(e -> new ComparaisonSaisons.ProduitSaison(
                        e.getKey().split("\\|")[0],
                        e.getKey().split("\\|")[1],
                        e.getValue().setScale(3, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(ComparaisonSaisons.ProduitSaison::typeProduit))
                .toList();

        return new ComparaisonSaisons(annee, production, productives.size(), rendement,
                recoltesDeLAnnee.size(), parProduit);
    }
}
