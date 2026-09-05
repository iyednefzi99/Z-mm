package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corps de requete pour enregistrer un traitement sanitaire (SPRINT-20).
 *
 * @param produit           nom commercial, tel qu'il figure sur l'emballage
 * @param substanceActive   ce qui permet de raisonner l'alternance et d'eviter la resistance
 * @param cible             varroa, loque, nosema... ce contre quoi on traite
 * @param dateFin           {@code null} tant que le traitement court
 * @param delaiCarenceJours duree pendant laquelle le miel ne peut pas etre recolte
 * @param visiteId          visite d'ou provient l'acte, si saisi depuis un rapport
 * @param ordonnanceVeterinaire veterinaire signataire (SPRINT-28) : sans lui, la
 *                          reference d'ordonnance n'etait pas verifiable
 * @param ordonnanceDate    date de l'ordonnance. La base refuse une date sans
 *                          reference ; l'inverse reste permis
 */
public record TraitementCorps(
        @NotNull Long rucheId,
        @NotNull Long agentId,
        Long visiteId,
        @NotBlank @Size(max = 120) String produit,
        @Size(max = 120) String substanceActive,
        @NotNull
        @Pattern(regexp = "varroa|loque_americaine|loque_europeenne|nosema"
                + "|petit_coleoptere|fausse_teigne|frelon|autre")
        String cible,
        @Positive BigDecimal dose,
        @Pattern(regexp = "mg|g|ml|l|laniere|plaquette") String doseUnite,
        @NotNull LocalDate dateDebut,
        LocalDate dateFin,
        @Min(0) @Max(365) Integer delaiCarenceJours,
        @Size(max = 120) String ordonnance,
        @Size(max = 120) String ordonnanceVeterinaire,
        LocalDate ordonnanceDate,
        String note) {
}
