package com.zumm.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Source de donnees meteo pour un point geographique (US-029). Abstraction pour
 * decoupler le service de l'API externe et rester testable hors-ligne.
 *
 * <p>Un seul appel rend les conditions courantes ET les previsions : le
 * fournisseur externe sait les servir ensemble, et deux methodes distinctes
 * auraient impose deux allers-retours reseau pour un ecran qui affiche les deux.
 */
public interface FournisseurMeteo {

    /**
     * Releve au point donne, ou {@link Optional#empty()} si indisponible.
     *
     * @param joursPrevision nombre de jours de prevision demandes ; {@code 0} pour
     *                       n'obtenir que les conditions courantes
     */
    Optional<Releve> releve(double latitude, double longitude, int joursPrevision);

    /**
     * Releve complet : l'instantane, et la liste des jours a venir (vide si aucune
     * prevision n'a ete demandee ou si le fournisseur n'en a pas rendu).
     */
    record Releve(Meteo courante, List<Jour> previsions) {
    }

    /** Instantane meteo brut (temperature en °C, humidite en %, vent en km/h). */
    record Meteo(double temperatureCelsius, Integer humiditePourcent, Double ventKmh) {
    }

    /**
     * Prevision d'une journee. Les champs sont des objets et non des primitifs :
     * une API meteo peut rendre une serie incomplete, et {@code null} dit
     * « inconnu » la ou {@code 0.0} dirait « pas de pluie ».
     */
    record Jour(
            LocalDate date,
            Double temperatureMinCelsius,
            Double temperatureMaxCelsius,
            Double precipitationsMm,
            Double ventMaxKmh) {
    }
}
