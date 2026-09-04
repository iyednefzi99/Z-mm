package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ouverture d'un partage de telemetrie (SPRINT-26, lot F1).
 *
 * @param rucheId    la ruche partagee — UNE seule, jamais l'exploitation
 * @param libelle    a qui, et pourquoi : c'est ce qui rendra la revocation
 *                   decidable dans six mois
 * @param dureeJours bornee a un an, comme l'abonnement iCalendar : il n'existe
 *                   pas d'option « sans expiration »
 */
public record PartageCorps(
        @NotNull Long rucheId,
        @NotBlank @Size(max = 80) String libelle,
        @NotNull @Min(1) @Max(365) Integer dureeJours) {
}
