package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Le rucher vu d'un coup (SPRINT-23, lot B).
 *
 * <p>Le niveau intermediaire de la « vision a trois niveaux » du §7 : personne ne
 * se deplace pour une ruche ni pour une exploitation — on va au rucher.
 *
 * @param santeMoyenne     moyenne des indices sur les seules colonies EVALUEES,
 *                         ou {@code null} si aucune ne l'est. Compter une colonie
 *                         non visitee comme 0 ferait chuter un rucher qu'on n'a
 *                         pas encore vu ; comme 100, il mentirait dans l'autre sens
 * @param coloniesEvaluees combien de colonies ont reellement nourri cette moyenne
 * @param risqueEssaimageMax le PIRE des risques, pas leur moyenne : une colonie
 *                         sur le point d'essaimer ne se compense pas avec neuf
 *                         colonies calmes
 * @param productionKg     recolte des douze derniers mois, tout le rucher
 */
public record SyntheseRucher(
        Long siteId,
        String siteNom,
        String ville,
        String priorite,
        int nbRuches,
        int nbActives,
        Integer santeMoyenne,
        int coloniesEvaluees,
        Integer risqueEssaimageMax,
        int ruchesSousCarence,
        long alertesOuvertes,
        long tachesOuvertes,
        BigDecimal productionKg) {
}
