package com.zumm.web.dto;

import java.time.Instant;

/**
 * Contexte meteo local d'un site (US-029). La source indique si les valeurs
 * proviennent du fournisseur externe ({@code open-meteo}) ou d'une estimation
 * deterministe hors-ligne ({@code simulation}).
 */
public record MeteoReponse(
        Long siteId,
        double latitude,
        double longitude,
        double temperatureCelsius,
        Integer humiditePourcent,
        Double ventKmh,
        String source,
        Instant instant) {
}
