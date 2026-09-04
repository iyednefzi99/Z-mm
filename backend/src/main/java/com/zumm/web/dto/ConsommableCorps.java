package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Un consommable saisi ou modifie (SPRINT-27, lot E).
 *
 * @param seuilAlerte quantite sous laquelle il faut racheter. Zero est une
 *                    valeur valable — « prevenez-moi quand il n'y en a plus » —
 *                    mais c'est un choix, pas un defaut subi
 */
public record ConsommableCorps(
        @NotBlank @Size(max = 120) String libelle,
        @NotNull
        @Pattern(regexp = "sirop|candi|traitement|cire_gaufree|pot|etiquette"
                + "|cadre|protection|autre")
        String categorie,
        @NotNull @PositiveOrZero BigDecimal quantite,
        @NotNull @Pattern(regexp = "kg|l|unite") String unite,
        @NotNull @PositiveOrZero BigDecimal seuilAlerte,
        String note) {
}
