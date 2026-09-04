package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une depense saisie (SPRINT-27, lot E).
 *
 * <p>Ni fournisseur, ni numero de piece, ni TVA : la frontiere du produit
 * s'arrete a la rentabilite par ruche. Chacun de ces champs appellerait le
 * suivant, et le troisieme rendrait le module obligatoire pour boucler un
 * exercice.
 *
 * @param montantEur POSITIF ou nul. Une depense negative est une recette, et les
 *                   recettes se calculent depuis les recoltes
 */
public record DepenseCorps(
        @NotBlank @Size(max = 160) String libelle,
        @NotNull
        @Pattern(regexp = "materiel|consommable|traitement|nourrissement|cheptel"
                + "|transport|analyse|assurance|formation|autre")
        String categorie,
        @NotNull @PositiveOrZero BigDecimal montantEur,
        @NotNull LocalDate dateDepense,
        Long rucheId,
        Long siteId,
        String note) {
}
