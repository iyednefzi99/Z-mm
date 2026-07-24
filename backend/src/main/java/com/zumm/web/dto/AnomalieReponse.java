package com.zumm.web.dto;

import com.zumm.domain.TypeIndicateur;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Resultat de la detection d'anomalie adaptative EWMA (US-034) pour une ruche et
 * un indicateur : ligne de base courante, ecart-type, et points juges anormaux
 * (|z| au-dela du seuil).
 *
 * @param alpha    facteur de lissage EWMA (poids de la derniere mesure)
 * @param seuilZ   seuil de z-score au-dela duquel un point est une anomalie
 * @param baseline moyenne EWMA courante (ligne de base), null si serie vide
 */
public record AnomalieReponse(
        Long rucheId,
        TypeIndicateur typeIndicateur,
        double alpha,
        double seuilZ,
        Double baseline,
        Double ecartType,
        int nombrePoints,
        List<PointAnomalie> anomalies) {

    /** Point de mesure signale comme anormal. */
    public record PointAnomalie(Instant instant, BigDecimal valeur, double zScore) {
    }
}
