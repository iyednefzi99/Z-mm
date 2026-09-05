package com.zumm.web.dto;

import com.zumm.domain.ReleveObservation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Valeur relevee pour un point d'observation, sur une visite (SPRINT-28).
 *
 * <p>Exactement un des deux champs est renseigne : {@code coche} pour un point
 * booleen, {@code niveau} pour un point d'echelle. Le service refuse l'autre —
 * une intensite sur une case a cocher ne veut rien dire, et l'accepter
 * silencieusement produirait des colonnes a moitie remplies dans toute
 * statistique construite ensuite.
 *
 * <p><strong>Ne pas envoyer un point n'est pas l'envoyer a « non ».</strong>
 * L'absence dit que le point n'a pas ete regarde ; {@code coche = false} dit
 * qu'il a ete regarde et qu'il etait absent.
 */
public record PointReleve(
        @NotBlank @Size(max = 40) String code,
        Boolean coche,
        @Min(0) @Max(3) Integer niveau) {

    public static PointReleve de(ReleveObservation r) {
        return new PointReleve(
                r.getId().getPointCode(),
                r.getValeurBool(),
                r.getValeurEchelle() == null ? null : r.getValeurEchelle().intValue());
    }
}
