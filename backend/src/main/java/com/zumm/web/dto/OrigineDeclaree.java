package com.zumm.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Part d'origine declaree a la constitution d'un lot (US-056).
 *
 * @param recolteId   recolte d'origine, ou {@code null} pour du miel acquis a un tiers
 * @param paysOrigine code ISO 3166-1 alpha-2 (« FR », « ES »…)
 * @param pourcentage part de cette origine dans le lot, en points de pourcentage
 */
public record OrigineDeclaree(
        Long recolteId,
        @NotNull
        @Pattern(regexp = "^[A-Z]{2}$", message = "Code pays ISO 3166-1 alpha-2 attendu (ex. FR).")
        String paysOrigine,
        @NotNull @Positive BigDecimal pourcentage) {
}
