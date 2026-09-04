package com.zumm.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Enregistrement d'une capture d'essaim (SPRINT-21).
 *
 * <p>Aucune coordonnee : {@code lieu} est un repere humain — « haie du voisin,
 * chemin des Vignes ». Voir la javadoc de {@code CaptureEssaim} pour la raison.
 *
 * @param agentId     qui a capture, obligatoire
 * @param rucheId     ruche dans laquelle l'essaim est loge, facultative
 * @param siteId      rucher d'accueil, facultatif
 * @param dateCapture obligatoire
 * @param origine     essaim naturel, piege, recuperation, signalement
 * @param lieu        repere libre
 * @param poidsKg     masse estimee de l'essaim (0 a 20 kg)
 * @param hauteurM    hauteur de la capture (0 a 60 m)
 * @param note        precision libre
 */
public record CaptureEssaimCorps(
        @NotNull Long agentId,
        Long rucheId,
        Long siteId,
        @NotNull LocalDate dateCapture,
        @NotNull @Pattern(regexp = "essaim_naturel|piege|recuperation|signalement|autre")
        String origine,
        @Size(max = 200) String lieu,
        @DecimalMin("0.0") @DecimalMax("20.0") BigDecimal poidsKg,
        @DecimalMin("0.0") @DecimalMax("60.0") BigDecimal hauteurM,
        @Size(max = 2000) String note) {
}
