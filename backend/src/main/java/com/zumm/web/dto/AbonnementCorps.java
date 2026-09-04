package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Demande d'abonnement iCalendar (SPRINT-21).
 *
 * @param agentId    agent dont l'agenda sera publie
 * @param libelle    a quoi sert cet abonnement — « Telephone », « Outlook du
 *                   bureau ». Obligatoire : un jeton qu'on ne sait plus situer ne
 *                   se revoque jamais
 * @param dureeJours duree de validite, bornee a un an. Il n'y a pas d'option
 *                   « sans expiration », et c'est le point : un abonnement
 *                   perpetuel est un secret perpetuel
 */
public record AbonnementCorps(
        @NotNull Long agentId,
        @NotBlank @Size(max = 80) String libelle,
        @NotNull @Min(1) @Max(365) Integer dureeJours) {
}
