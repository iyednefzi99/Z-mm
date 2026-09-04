package com.zumm.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Plan de transhumance (SPRINT-21).
 *
 * @param siteId               rucher a deplacer, obligatoire
 * @param agentId              qui conduit l'operation, obligatoire
 * @param datePrevue           obligatoire
 * @param heurePrevue          facultative
 * @param vehicule             description libre du moyen de transport
 * @param capaciteRuches       ce que le vehicule porte en un voyage
 * @param nbRuches             ce qu'on compte deplacer
 * @param destinationLibelle   ou l'on va, en clair — obligatoire
 * @param destinationLatitude  facultative ; sans elle le plan ne se realise pas
 * @param destinationLongitude facultative, et indissociable de la latitude
 * @param note                 precision libre
 */
public record TransportCorps(
        @NotNull Long siteId,
        @NotNull Long agentId,
        @NotNull LocalDate datePrevue,
        LocalTime heurePrevue,
        @Size(max = 80) String vehicule,
        @Min(1) Integer capaciteRuches,
        @Min(0) Integer nbRuches,
        @NotBlank @Size(max = 160) String destinationLibelle,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal destinationLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal destinationLongitude,
        @Size(max = 2000) String note) {
}
