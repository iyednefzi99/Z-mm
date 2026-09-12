package com.zumm.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Declaration d'une zone traitee (SPRINT-33, lot K).
 *
 * <p>La geometrie arrive en GeoJSON, comme le versement d'occupation du sol :
 * un seul format d'entree geographique dans le produit, et PostGIS le valide a
 * l'insertion.
 *
 * <p>Le corps ne comporte deliberement <strong>aucun champ nommant une
 * personne</strong> — voir {@link ZoneTraiteeReponse}. Un champ libre `note`
 * existe, et c'est a l'exploitant de ne pas y ecrire ce que le modele refuse de
 * porter ; le produit ne peut pas l'en empecher, il peut ne pas l'y inviter.
 *
 * @param geometrie  Polygon ou MultiPolygon GeoJSON, normalise a l'insertion
 * @param origine    voisin_declare | observe | avis_officiel | autre
 */
public record ZoneTraiteeCorps(
        @NotNull JsonNode geometrie,
        @NotNull LocalDate dateTraitement,
        @Size(max = 120) String substance,
        @NotBlank
        @Pattern(regexp = "voisin_declare|observe|avis_officiel|autre")
        String origine,
        @Min(0) @Max(720) Integer delaiRentreeH,
        String note) {
}
