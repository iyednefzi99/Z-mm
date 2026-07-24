package com.zumm.web.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Synthese de pilotage et ROI (US-015). Agrege l'activite (visites, interventions
 * par motif), la production (poids total actuel, productivite moyenne) et les
 * alertes ouvertes, avec un indicateur de retour sur investissement.
 *
 * @param roi retour sur investissement indicatif (economie de reference documentee)
 */
public record SyntheseReponse(
        long nombreRuches,
        long nombreVisites,
        Map<String, Long> visitesParRaison,
        Double productiviteMoyenne,
        BigDecimal poidsTotalActuelKg,
        long alertesOuvertes,
        Roi roi) {

    /**
     * ROI indicatif : valorisation de la production moins le cout des interventions.
     *
     * @param roiPourcent (valeur − cout) / cout × 100, ou null si aucune intervention
     */
    public record Roi(
            BigDecimal valeurProductionEur,
            BigDecimal coutInterventionsEur,
            Double roiPourcent) {
    }
}
