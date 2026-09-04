package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Plan de deplacement d'un rucher (SPRINT-21, §1 de
 * {@code docs/ECART-CONCURRENTS.md}).
 *
 * <p>{@link EmplacementSite} sait ou un rucher a ETE ; il ne sait pas comment il
 * y va. Un transport est un PLAN : il precede le deplacement, porte le vehicule
 * et le creneau, et peut ne jamais avoir lieu.
 *
 * <p>Le realiser declenche exactement ce que fait
 * {@code POST /api/sites/&#123;id&#125;/demenagement} — les deux chemins convergent a
 * dessein : un rucher deplace laisse la meme trace, qu'il ait ete planifie ou
 * non.
 */
@Entity
@Table(name = "transport")
public class Transport extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @NotNull
    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    @Column(name = "heure_prevue")
    private LocalTime heurePrevue;

    @Size(max = 80)
    @Column(name = "vehicule", length = 80)
    private String vehicule;

    /**
     * Ce que le vehicule peut porter, et ce qu'on compte deplacer. L'ecart entre
     * les deux est toute l'information du plan : un camion de vingt places pour
     * quarante ruches, c'est deux voyages, et cela se decide avant de partir.
     */
    @Min(1)
    @Column(name = "capacite_ruches")
    private Integer capaciteRuches;

    @Min(0)
    @Column(name = "nb_ruches")
    private Integer nbRuches;

    @NotBlank
    @Size(max = 160)
    @Column(name = "destination_libelle", nullable = false, length = 160)
    private String destinationLibelle;

    /**
     * Coordonnees de destination, facultatives : on planifie souvent vers un
     * emplacement qu'on n'a pas encore releve. Sans elles, le transport se
     * planifie mais ne se REALISE pas — le service le dit alors en clair plutot
     * que d'ouvrir un emplacement sans position.
     */
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Column(name = "destination_latitude", precision = 9, scale = 6)
    private BigDecimal destinationLatitude;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Column(name = "destination_longitude", precision = 9, scale = 6)
    private BigDecimal destinationLongitude;

    @NotNull
    @Pattern(regexp = "prevu|realise|annule")
    @Column(name = "statut", nullable = false, length = 10)
    private String statut = "prevu";

    @Size(max = 2000)
    @Column(name = "note")
    private String note;

    protected Transport() {
        // Requis par JPA.
    }

    public Transport(Site site, Agent agent, LocalDate datePrevue, String destinationLibelle) {
        this.site = site;
        this.agent = agent;
        this.datePrevue = datePrevue;
        this.destinationLibelle = destinationLibelle;
    }

    /** Le plan porte-t-il de quoi ouvrir un emplacement ? */
    public boolean destinationLocalisee() {
        return destinationLatitude != null && destinationLongitude != null;
    }

    /** Nombre de voyages qu'impose la capacite, ou {@code null} si l'un des deux manque. */
    public Integer voyages() {
        if (capaciteRuches == null || nbRuches == null || nbRuches == 0) {
            return null;
        }
        return (nbRuches + capaciteRuches - 1) / capaciteRuches;
    }

    public Site getSite() {
        return site;
    }

    public Agent getAgent() {
        return agent;
    }

    public LocalDate getDatePrevue() {
        return datePrevue;
    }

    public void setDatePrevue(LocalDate datePrevue) {
        this.datePrevue = datePrevue;
    }

    public LocalTime getHeurePrevue() {
        return heurePrevue;
    }

    public void setHeurePrevue(LocalTime heurePrevue) {
        this.heurePrevue = heurePrevue;
    }

    public String getVehicule() {
        return vehicule;
    }

    public void setVehicule(String vehicule) {
        this.vehicule = vehicule;
    }

    public Integer getCapaciteRuches() {
        return capaciteRuches;
    }

    public void setCapaciteRuches(Integer capaciteRuches) {
        this.capaciteRuches = capaciteRuches;
    }

    public Integer getNbRuches() {
        return nbRuches;
    }

    public void setNbRuches(Integer nbRuches) {
        this.nbRuches = nbRuches;
    }

    public String getDestinationLibelle() {
        return destinationLibelle;
    }

    public void setDestinationLibelle(String destinationLibelle) {
        this.destinationLibelle = destinationLibelle;
    }

    public BigDecimal getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(BigDecimal destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public BigDecimal getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(BigDecimal destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
