package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Un equipement saisi ou modifie (SPRINT-27, lot E).
 *
 * @param periodiciteJours periodicite d'entretien. NULLE = materiel qui ne
 *                         s'entretient PAS, et non « quand on y pense » : le
 *                         moteur de regles ne proposera rien pour lui
 */
public record MaterielCorps(
        @NotBlank @Size(max = 120) String libelle,
        @NotNull
        @Pattern(regexp = "ruche|hausse|cadre|extracteur|maturateur|enfumoir"
                + "|protection|vehicule|balance|autre")
        String categorie,
        @Min(1) int quantite,
        Long siteId,
        @Pattern(regexp = "neuf|bon|a_reviser|hors_service") String etat,
        @Min(1) @Max(3650) Integer periodiciteJours,
        LocalDate derniereMaintenance,
        String note) {
}
