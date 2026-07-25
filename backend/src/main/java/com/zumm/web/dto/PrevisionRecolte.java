package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Prévision de récolte d'une ruche (US-042, SPRINT-09).
 *
 * <p>Extrapole la tendance du poids (indicateur de production) à partir des mesures
 * connues : une régression linéaire donne le gain journalier moyen, d'où une
 * projection à 7 jours et un signal de tendance. Sert de widget au tableau de bord
 * production pour anticiper le moment de la récolte.
 *
 * @param rucheId          identifiant de la ruche
 * @param rucheModele      modèle de la ruche
 * @param poidsActuelKg    dernière mesure de poids connue, ou null si aucune
 * @param tendanceKgParJour gain journalier moyen (régression), ou null si &lt; 2 mesures
 * @param projection7jKg   poids projeté dans 7 jours, ou null si tendance inconnue
 * @param tendance         HAUSSE / STABLE / BAISSE / INCONNUE
 * @param nombreMesures    nombre de mesures de poids ayant servi au calcul
 */
public record PrevisionRecolte(
        Long rucheId,
        String rucheModele,
        BigDecimal poidsActuelKg,
        BigDecimal tendanceKgParJour,
        BigDecimal projection7jKg,
        String tendance,
        long nombreMesures) {

    public static final String HAUSSE = "hausse";
    public static final String STABLE = "stable";
    public static final String BAISSE = "baisse";
    public static final String INCONNUE = "inconnue";
}
