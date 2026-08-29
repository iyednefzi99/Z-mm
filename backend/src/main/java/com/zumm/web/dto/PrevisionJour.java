package com.zumm.web.dto;

import java.time.LocalDate;

/**
 * Prevision meteo d'une journee pour un site (US-029).
 *
 * <p>Les minimales et maximales sont rendues plutot qu'une moyenne : ce sont elles
 * qui decident d'une visite. Une colonie ne se manipule pas sous 12 °C, et la
 * secretion de nectar s'arrete bien avant que la moyenne du jour ne le montre.
 * Les precipitations sont un cumul en millimetres, le vent une pointe en km/h.
 *
 * <p>Champs nullables : une serie meteo peut arriver incomplete, et {@code null}
 * dit « inconnu » la ou {@code 0} dirait « pas de pluie ».
 */
public record PrevisionJour(
        LocalDate date,
        Double temperatureMinCelsius,
        Double temperatureMaxCelsius,
        Double precipitationsMm,
        Double ventMaxKmh) {
}
