package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Corps de requete pour une serie d'elevage (SPRINT-29, lot D).
 *
 * <p>Les trois derniers comptes sont facultatifs, et se remplissent au fil des
 * jours : on greffe un lundi, on compte les acceptations le vendredi, les
 * naissances douze jours plus tard, les fecondations trois semaines apres. Les
 * exiger a la creation obligerait a inventer trois chiffres.
 */
public record SerieCorps(
        @NotBlank @Size(max = 60) String nom,
        @NotNull LocalDate dateGreffage,
        Long soucheId,
        Long rucheEleveuseId,
        @Pattern(regexp = "greffage|picking|cupularve|essaim_artificiel|autre") String methode,
        @NotNull @Positive Integer nbGreffees,
        @PositiveOrZero Integer nbAcceptees,
        @PositiveOrZero Integer nbNees,
        @PositiveOrZero Integer nbFecondees,
        String note) {
}
