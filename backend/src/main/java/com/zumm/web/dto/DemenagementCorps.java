package com.zumm.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Deplacement d'un rucher vers un nouvel emplacement (SPRINT-21, transhumance).
 *
 * <p>Une operation dediee plutot qu'une simple mise a jour des coordonnees du
 * site : la difference entre « corriger une position mal saisie » et « le rucher
 * a demenage » n'est pas dans les champs, elle est dans l'INTENTION. Seule la
 * seconde doit clore un emplacement et en ouvrir un autre ; les confondre
 * remplirait l'historique de fausses transhumances a chaque faute de frappe
 * corrigee.
 *
 * @param latitude  nouvelle position, obligatoire
 * @param longitude nouvelle position, obligatoire
 * @param altitude  facultative
 * @param dateDebut date d'occupation du nouvel emplacement, obligatoire
 * @param motif     pourquoi le rucher bouge, facultatif
 * @param note      precision libre, facultative
 */
public record DemenagementCorps(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("-500.0") @DecimalMax("9000.0") BigDecimal altitude,
        @NotNull LocalDate dateDebut,
        @Pattern(regexp = "installation|transhumance|miellee|securite|reglementaire|autre")
        String motif,
        @Size(max = 2000) String note) {
}
