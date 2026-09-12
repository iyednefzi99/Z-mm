package com.zumm.web.dto;

import java.time.LocalDate;

/**
 * Une zone traitee declaree (SPRINT-33, lot K).
 *
 * <p><strong>Aucune identite de tiers.</strong> Ni nom, ni adresse, ni contact :
 * le §13 refuse un annuaire de voisins, et ce refus tient. Une zone traitee est
 * un polygone, une date, et une substance quand on la connait. Ajouter
 * « qui » transformerait la couche en fichier de tiers.
 *
 * @param origine       voisin_declare | observe | avis_officiel | autre. Pas
 *                      pour classer les tiers, mais parce qu'un avis officiel et
 *                      un « il me semble avoir vu un pulverisateur » ne fondent
 *                      pas la meme decision
 * @param substance     telle qu'elle a ete dite, ou {@code null} — « ils ont
 *                      traite, je ne sais pas avec quoi » est l'information la
 *                      plus frequente, et la refuser reviendrait a la perdre
 * @param delaiRentreeH delai de rentree reglementaire en heures, ou {@code null}.
 *                      {@code null} n'est PAS zero : zero se lirait « on peut y
 *                      aller », qui est precisement ce qu'on ne sait pas
 * @param surfaceHa     surface geodesique de la zone
 */
public record ZoneTraiteeReponse(
        Long id,
        LocalDate dateTraitement,
        String substance,
        String origine,
        Integer delaiRentreeH,
        String note,
        java.math.BigDecimal surfaceHa) {
}
