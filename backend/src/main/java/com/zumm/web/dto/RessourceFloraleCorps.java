package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Ressource florale declaree autour d'un rucher (SPRINT-21).
 *
 * @param ressource valeur du referentiel, obligatoire
 * @param distanceM distance approximative au rucher, en metres (0 a 20 000)
 * @param moisDebut debut de floraison en mois (1-12), facultatif
 * @param moisFin   fin de floraison ; peut etre INFERIEUR au debut, une
 *                  floraison pouvant enjamber l'annee (novembre a fevrier)
 * @param note      precision libre, facultative
 */
public record RessourceFloraleCorps(
        @NotNull
        @Pattern(regexp = "colza|tournesol|acacia|chataignier|tilleul|lavande|bruyere|luzerne"
                + "|sarrasin|verger|agrumes|eucalyptus|thym|romarin|jujubier|palmier_dattier"
                + "|prairie|foret|garrigue|autre")
        String ressource,
        @Min(0) @Max(20_000) Integer distanceM,
        @Min(1) @Max(12) Integer moisDebut,
        @Min(1) @Max(12) Integer moisFin,
        @Size(max = 2000) String note) {
}
