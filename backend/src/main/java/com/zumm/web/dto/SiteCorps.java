package com.zumm.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Corps de requete pour creer ou mettre a jour un site (US-003 ; identite postale
 * et ressources ajoutees au SPRINT-21).
 *
 * <p>Les bornes de coordonnees sont verifiees ici (rejet precoce) ; l'ordre des
 * dates de cycle de vie (US-006) est verifie par le service, car il croise
 * plusieurs champs.
 *
 * <p>Les coordonnees restent modifiables par cette route : c'est la CORRECTION
 * d'une position mal saisie. Un vrai deplacement passe par
 * {@code POST /api/sites/{id}/demenagement} ({@link DemenagementCorps}), seul a
 * ecrire l'historique — confondre les deux remplirait l'historique de fausses
 * transhumances a chaque faute de frappe corrigee.
 *
 * @param nom              nom du site, obligatoire
 * @param fermeId          ferme d'appartenance, obligatoire ; doit exister dans le tenant
 * @param latitude         degres decimaux, obligatoire
 * @param longitude        degres decimaux, obligatoire
 * @param altitude         metres, facultative
 * @param dateMiseEnOeuvre debut d'exploitation, obligatoire
 * @param dateDemenagement facultative, jamais anterieure a la mise en oeuvre
 * @param dateCloture      facultative, jamais anterieure a la mise en oeuvre
 * @param adresseRue       voie du rucher, facultative — aussi sensible que la position
 * @param codePostal       facultatif
 * @param ville            commune, facultative
 * @param pays             code ISO 3166-1 alpha-2 (FR, TN, MA...), facultatif
 * @param typeSite         sedentaire, transhumance, fecondation, elevage...
 * @param exposition       orientation dominante du rucher
 * @param ressources       sources de nectar declarees ; la liste remplace la
 *                         precedente en entier, comme la composition d'une ruche
 */
public record SiteCorps(
        @NotBlank @Size(max = 120) String nom,
        @NotNull Long fermeId,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @DecimalMin("-500.0") @DecimalMax("9000.0") BigDecimal altitude,
        /**
         * Rayon de butinage de CE rucher, en kilometres (SPRINT-32).
         *
         * <p>Facultatif : absent, le defaut de `ConfigZumm.ini` s'applique.
         * Borne a 15 km — au-dela, une abeille ne rentre pas.
         */
        @DecimalMin("0.5") @DecimalMax("15.0") BigDecimal rayonButinageKm,
        @NotNull LocalDate dateMiseEnOeuvre,
        LocalDate dateDemenagement,
        LocalDate dateCloture,
        @Size(max = 160) String adresseRue,
        @Size(max = 12) String codePostal,
        @Size(max = 80) String ville,
        @Pattern(regexp = "[A-Z]{2}", message = "Le pays est un code ISO a deux lettres majuscules.")
        String pays,
        @Pattern(regexp = "sedentaire|transhumance|fecondation|elevage|conservatoire|autre")
        String typeSite,
        @Pattern(regexp = "nord|nord_est|est|sud_est|sud|sud_ouest|ouest|nord_ouest")
        String exposition,
        @Valid List<RessourceFloraleCorps> ressources,
        /** basse | normale | haute. Absente, elle vaut `normale`. */
        @Pattern(regexp = "basse|normale|haute") String priorite,
        /**
         * aucune | faible | correcte | bonne (SPRINT-24). Absente, elle reste
         * INCONNUE : un defaut a « correcte » ferait partir un apiculteur sans
         * emport sur un rucher en zone blanche.
         */
        @Pattern(regexp = "aucune|faible|correcte|bonne") String couvertureReseau) {
}
