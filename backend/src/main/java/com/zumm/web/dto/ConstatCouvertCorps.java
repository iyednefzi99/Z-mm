package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Ce que le terrain a montre sur une parcelle (SPRINT-33, lot K).
 *
 * <p>La date est OBLIGATOIRE, et ce n'est pas une formalite : un constat sans
 * date ne se situe pas dans le temps, et la question que le ground truthing pose
 * est justement temporelle — la couche de 2024 decrivait-elle encore le sol au
 * printemps 2026 ? La base tient la meme regle
 * ({@code ck_couvert_constat_date}).
 *
 * @param classeConstatee taxonomie FERMEE, identique a celle du versement. Le
 *                        terrain corrige la source ; il n'invente pas une classe
 *                        de plus, sans quoi deux exploitations redeviendraient
 *                        incomparables
 * @param constateLe      jour de la visite
 * @param note            ce que la classe ne dit pas — « colza retourne, seme en
 *                        mais fin avril »
 */
public record ConstatCouvertCorps(
        @NotBlank
        @Pattern(regexp = "culture|prairie|foret|lande|verger|vigne"
                + "|eau|urbain|sol_nu|autre")
        String classeConstatee,
        @NotNull LocalDate constateLe,
        String note) {
}
