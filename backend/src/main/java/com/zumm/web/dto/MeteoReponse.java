package com.zumm.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Contexte meteo local d'un site (US-029) : conditions courantes et previsions des
 * prochains jours. La source indique si les valeurs proviennent du fournisseur
 * externe ({@code open-meteo}) ou d'une estimation deterministe hors-ligne
 * ({@code simulation}).
 *
 * <p>{@code previsions} est vide — jamais {@code null} — quand aucune prevision
 * n'a ete demandee : une liste absente obligerait chaque appelant a se garder.
 */
public record MeteoReponse(
        Long siteId,
        double latitude,
        double longitude,
        double temperatureCelsius,
        Integer humiditePourcent,
        Double ventKmh,
        String source,
        Instant instant,
        List<PrevisionJour> previsions) {
}
