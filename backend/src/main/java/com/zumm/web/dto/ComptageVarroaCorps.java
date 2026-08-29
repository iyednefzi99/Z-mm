package com.zumm.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * Corps de requete pour enregistrer un comptage de varroa (SPRINT-20).
 *
 * <p>Le denominateur attendu depend de la methode, et un seul des deux doit etre
 * fourni :
 *
 * <ul>
 *   <li>{@code lange} — chute naturelle : {@code joursExposition} ;
 *   <li>tout le reste — echantillon : {@code abeillesEchantillon}.
 * </ul>
 *
 * <p>La regle est verifiee par le service (message explicable) et par la base
 * (contrainte {@code ck_varroa_denominateur}, garantie dure).
 */
public record ComptageVarroaCorps(
        @NotNull Long rucheId,
        @NotNull Long agentId,
        Long visiteId,
        @NotNull LocalDate dateComptage,
        @NotNull @Pattern(regexp = "lange|sucre_glace|alcool|co2|desoperculation") String methode,
        @NotNull @PositiveOrZero Integer varroasComptes,
        @Positive Integer abeillesEchantillon,
        @Positive Integer joursExposition,
        String note) {
}
