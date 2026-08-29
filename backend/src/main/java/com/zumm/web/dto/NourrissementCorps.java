package com.zumm.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corps de requete pour enregistrer un nourrissement (SPRINT-20).
 *
 * @param typeAliment les deux sirops sont distincts : le 1:1 stimule la ponte au
 *                    printemps, le 2:1 constitue les reserves d'hiver
 * @param motif       stimulation, hivernage, disette, secours, transhumance
 */
public record NourrissementCorps(
        @NotNull Long rucheId,
        @NotNull Long agentId,
        Long visiteId,
        @NotNull LocalDate dateApport,
        @NotNull
        @Pattern(regexp = "sirop_1_1|sirop_2_1|candi|pollen|substitut_pollen|miel|eau")
        String typeAliment,
        @NotNull @Positive BigDecimal quantite,
        @NotNull @Pattern(regexp = "kg|g|l|ml") String quantiteUnite,
        @Pattern(regexp = "stimulation|hivernage|disette|secours|transhumance|autre") String motif,
        String note) {
}
