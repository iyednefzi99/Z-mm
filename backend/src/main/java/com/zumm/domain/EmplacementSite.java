package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Emplacement occupe par un rucher pendant une periode (SPRINT-21, §1 de
 * {@code docs/ECART-CONCURRENTS.md}).
 *
 * <p>{@code site.dateDemenagement} savait qu'un rucher avait bouge ; il ne savait
 * pas d'ou. Un site transhume perdait donc son passe a chaque deplacement.
 *
 * <p>Le modele est celui d'une PERIODE : une ligne par emplacement occupe, la
 * ligne courante etant la seule dont {@link #dateFin} est nulle — un index unique
 * partiel l'impose en base, pas seulement au service.
 *
 * <p>{@code site.latitude/longitude} reste la position COURANTE. C'est une
 * denormalisation assumee : toutes les requetes spatiales (PostGIS, grappes,
 * voisins, tournee) la lisent, et les faire passer par un historique les
 * ralentirait toutes pour un gain nul.
 */
@Entity
@Table(name = "emplacement_site")
public class EmplacementSite extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

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
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** Nulle = emplacement courant. Etat normal d'un rucher en place, pas une saisie incomplete. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Pattern(regexp = "installation|transhumance|miellee|securite|reglementaire|autre")
    @Column(name = "motif", length = 20)
    private String motif;

    @Size(max = 2000)
    @Column(name = "note")
    private String note;

    protected EmplacementSite() {
        // Requis par JPA.
    }

    public EmplacementSite(Site site, BigDecimal latitude, BigDecimal longitude,
            BigDecimal altitude, LocalDate dateDebut, String motif) {
        this.site = site;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.dateDebut = dateDebut;
        this.motif = motif;
    }

    /** Emplacement encore occupe : la question que pose toute vue d'historique. */
    public boolean courant() {
        return dateFin == null;
    }

    public Site getSite() {
        return site;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getAltitude() {
        return altitude;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public String getMotif() {
        return motif;
    }

    public void setMotif(String motif) {
        this.motif = motif;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
