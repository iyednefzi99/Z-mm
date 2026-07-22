package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Site — emplacement d'apiculture geolocalise (US-003).
 *
 * <p>La geolocalisation est stockee en degres decimaux ; la colonne PostGIS
 * derivee {@code geog} (migration V2) n'est pas mappee ici : elle est calculee et
 * indexee par la base pour les requetes spatiales, et n'a pas a transiter par JPA.
 *
 * <p>Les bornes de coordonnees et l'ordre des dates de cycle de vie (US-006) sont
 * verifies a la fois par Bean Validation (rejet precoce, message clair) et par des
 * contraintes {@code CHECK} en base (garantie ultime).
 */
@Entity
@Table(name = "site")
public class Site extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "nom", nullable = false, length = 120)
    private String nom;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ferme_id", nullable = false)
    private Ferme ferme;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @DecimalMin("-500.0")
    @DecimalMax("9000.0")
    @Column(name = "altitude", precision = 7, scale = 2)
    private BigDecimal altitude;

    @NotNull
    @Column(name = "date_mise_en_oeuvre", nullable = false)
    private LocalDate dateMiseEnOeuvre;

    @Column(name = "date_demenagement")
    private LocalDate dateDemenagement;

    @Column(name = "date_cloture")
    private LocalDate dateCloture;

    protected Site() {
        // Requis par JPA.
    }

    public Site(String nom, Ferme ferme, BigDecimal latitude, BigDecimal longitude,
                LocalDate dateMiseEnOeuvre) {
        this.nom = nom;
        this.ferme = ferme;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dateMiseEnOeuvre = dateMiseEnOeuvre;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Ferme getFerme() {
        return ferme;
    }

    public void setFerme(Ferme ferme) {
        this.ferme = ferme;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getAltitude() {
        return altitude;
    }

    public void setAltitude(BigDecimal altitude) {
        this.altitude = altitude;
    }

    public LocalDate getDateMiseEnOeuvre() {
        return dateMiseEnOeuvre;
    }

    public void setDateMiseEnOeuvre(LocalDate dateMiseEnOeuvre) {
        this.dateMiseEnOeuvre = dateMiseEnOeuvre;
    }

    public LocalDate getDateDemenagement() {
        return dateDemenagement;
    }

    public void setDateDemenagement(LocalDate dateDemenagement) {
        this.dateDemenagement = dateDemenagement;
    }

    public LocalDate getDateCloture() {
        return dateCloture;
    }

    public void setDateCloture(LocalDate dateCloture) {
        this.dateCloture = dateCloture;
    }
}
