package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Evenement du journal de la reine d'une ruche (US-032).
 *
 * <p>Le statut et la couleur de marquage sont contraints (Bean Validation +
 * CHECK en base). La couleur suit le code international : blanc, jaune, rouge,
 * vert, bleu selon l'annee de naissance de la reine.
 */
@Entity
@Table(name = "suivi_reine")
public class SuiviReine extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @Column(name = "date_evenement", nullable = false)
    private LocalDate dateEvenement;

    @NotNull
    @Pattern(regexp = "introduite|en_ponte|remplacee|disparue|essaimee")
    @Column(name = "statut", nullable = false, length = 20)
    private String statut;

    @Pattern(regexp = "blanc|jaune|rouge|vert|bleu")
    @Column(name = "couleur_marquage", length = 10)
    private String couleurMarquage;

    @Column(name = "annee_naissance")
    private Integer anneeNaissance;

    @Size(max = 60)
    @Column(name = "race", length = 60)
    private String race;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected SuiviReine() {
        // Requis par JPA.
    }

    public SuiviReine(Ruche ruche, LocalDate dateEvenement, String statut) {
        this.ruche = ruche;
        this.dateEvenement = dateEvenement;
        this.statut = statut;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public LocalDate getDateEvenement() {
        return dateEvenement;
    }

    public void setDateEvenement(LocalDate dateEvenement) {
        this.dateEvenement = dateEvenement;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getCouleurMarquage() {
        return couleurMarquage;
    }

    public void setCouleurMarquage(String couleurMarquage) {
        this.couleurMarquage = couleurMarquage;
    }

    public Integer getAnneeNaissance() {
        return anneeNaissance;
    }

    public void setAnneeNaissance(Integer anneeNaissance) {
        this.anneeNaissance = anneeNaissance;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = race;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
