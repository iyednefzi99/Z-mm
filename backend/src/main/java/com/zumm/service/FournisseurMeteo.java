package com.zumm.service;

import java.util.Optional;

/**
 * Source de donnees meteo pour un point geographique (US-029). Abstraction pour
 * decoupler le service de l'API externe et rester testable hors-ligne.
 */
public interface FournisseurMeteo {

    /** Meteo courante au point donne, ou {@link Optional#empty()} si indisponible. */
    Optional<Meteo> courante(double latitude, double longitude);

    /** Instantane meteo brut (temperature en °C, humidite en %, vent en km/h). */
    record Meteo(double temperatureCelsius, Integer humiditePourcent, Double ventKmh) {
    }
}
