package com.zumm.web.dto;

import com.zumm.domain.Traitement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'un traitement sanitaire (SPRINT-20).
 *
 * @param dateRetrait fin de carence, calculee par la base
 *                    ({@code date_fin + delai_carence_jours})
 * @param sousCarence le miel de cette ruche est-il recoltable AUJOURD'HUI ?
 *                    Champ derive : le client n'a pas a refaire le calcul de
 *                    dates, et surtout pas a le refaire differemment
 */
public record TraitementReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        Long agentId,
        String agentNom,
        Long visiteId,
        String produit,
        String substanceActive,
        String cible,
        BigDecimal dose,
        String doseUnite,
        LocalDate dateDebut,
        LocalDate dateFin,
        Integer delaiCarenceJours,
        LocalDate dateRetrait,
        boolean sousCarence,
        String ordonnance,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static TraitementReponse de(Traitement t, LocalDate jour) {
        return new TraitementReponse(
                t.getId(),
                t.getRuche().getId(),
                t.getRuche().getModele(),
                t.getAgent().getId(),
                t.getAgent().getNom(),
                t.getVisite() == null ? null : t.getVisite().getId(),
                t.getProduit(),
                t.getSubstanceActive(),
                t.getCible(),
                t.getDose(),
                t.getDoseUnite(),
                t.getDateDebut(),
                t.getDateFin(),
                t.getDelaiCarenceJours(),
                t.getDateRetrait(),
                t.sousCarence(jour),
                t.getOrdonnance(),
                t.getNote(),
                t.getCreeLe(),
                t.getMajLe());
    }
}
