package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Ligne du tableau de bord production (US-013) : synthese du poids d'une ruche
 * (indicateur de production) sur l'ensemble des mesures connues, avec le drapeau
 * {@code sousSeuil} leve quand le poids courant passe sous le seuil d'alerte de
 * {@code ConfigZumm.ini}.
 *
 * @param productiviteMoyenne moyenne des evaluations 1-3 des visites, ou null si aucune
 */
public record LigneProduction(
        Long rucheId,
        String rucheModele,
        BigDecimal poidsActuelKg,
        BigDecimal poidsMinKg,
        BigDecimal poidsMaxKg,
        long nombreMesures,
        boolean sousSeuil,
        Double productiviteMoyenne) {
}
