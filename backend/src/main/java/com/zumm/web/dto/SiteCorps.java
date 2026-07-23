package com.zumm.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corps de requete pour creer ou mettre a jour un site (US-003).
 *
 * <p>Les bornes de coordonnees sont verifiees ici (rejet precoce) ; l'ordre des
 * dates de cycle de vie (US-006) est verifie par le service, car il croise
 * plusieurs champs.
 *
 * @param nom               nom du site, obligatoire
 * @param fermeId           ferme d'appartenance, obligatoire ; doit exister dans le tenant
 * @param latitude          degres decimaux, obligatoire
 * @param longitude         degres decimaux, obligatoire
 * @param altitude          metres, facultative
 * @param dateMiseEnOeuvre  debut d'exploitation, obligatoire
 * @param dateDemenagement  facultative, jamais anterieure a la mise en oeuvre
 * @param dateCloture       facultative, jamais anterieure a la mise en oeuvre
 */
public record SiteCorps(
        @NotBlank @Size(max = 120) String nom,
        @NotNull Long fermeId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("-500.0") @DecimalMax("9000.0") BigDecimal altitude,
        @NotNull LocalDate dateMiseEnOeuvre,
        LocalDate dateDemenagement,
        LocalDate dateCloture) {
}
