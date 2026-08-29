package com.zumm.web.dto;

import com.zumm.domain.Visite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Meteo <strong>figee</strong> sur une visite (SPRINT-20).
 *
 * <p>Recopiee du fournisseur au moment de la saisie, jamais rappelee ensuite.
 * C'est ce qui debloque la correlation meteo x production que
 * {@code docs/ECART-CONCURRENTS.md} listait en manque alors que les deux sources
 * existaient deja : {@code MeteoService} livrait la prevision, mais rien ne la
 * conservait au moment ou elle comptait.
 *
 * <p>Aller rechercher la meteo du 12 mars six mois plus tard rendrait la
 * correlation fausse — on obtiendrait la valeur reconstituee d'aujourd'hui, pas
 * celle qui a ete observee.
 *
 * @param source {@code open-meteo}, {@code simulation} (repli hors ligne) ou
 *               {@code saisie} (releve a la main). La distinguer evite de traiter
 *               une estimation comme une mesure.
 */
public record MeteoVisite(
        BigDecimal temperatureCelsius,
        @Min(0) @Max(100) Integer humiditePourcent,
        @PositiveOrZero BigDecimal ventKmh,
        @Pattern(regexp = "open-meteo|simulation|saisie") String source) {

    /** Extrait le releve d'une visite, ou {@code null} si aucun n'a ete fige. */
    public static MeteoVisite de(Visite v) {
        if (v.getMeteoTemperatureC() == null && v.getMeteoHumiditePct() == null
                && v.getMeteoVentKmh() == null && v.getMeteoSource() == null) {
            return null;
        }
        return new MeteoVisite(
                v.getMeteoTemperatureC(),
                v.getMeteoHumiditePct(),
                v.getMeteoVentKmh(),
                v.getMeteoSource());
    }

    /** Reporte le releve sur l'entite. */
    public void appliquerA(Visite v) {
        v.setMeteoTemperatureC(temperatureCelsius);
        v.setMeteoHumiditePct(humiditePourcent);
        v.setMeteoVentKmh(ventKmh);
        v.setMeteoSource(source);
    }
}
