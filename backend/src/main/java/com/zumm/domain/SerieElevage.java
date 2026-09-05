package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Série d'élevage : un lot de greffage (SPRINT-29, lot D).
 *
 * <p>Ferme la ligne « dates de greffage, suivi d'élevage » du §7.
 *
 * <p><strong>Un lot, et non une reine à la fois.</strong> Ce que l'éleveur note,
 * c'est quarante cupules greffées un lundi, trente et une acceptées, vingt-huit
 * nées, vingt-deux fécondées. Le <strong>taux d'acceptation</strong> est le
 * chiffre qui décide de la méthode l'année suivante, et il n'existe qu'au niveau
 * du lot.
 *
 * <p><strong>Les comptes sont saisis, jamais déduits des reines
 * enregistrées.</strong> On n'enregistre individuellement que les reines qu'on
 * garde ; les compter donnerait un taux d'acceptation faux, et toujours trop bas
 * — c'est-à-dire dans le sens qui ferait changer une méthode qui marchait.
 */
@Entity
@Table(name = "serie_elevage")
public class SerieElevage extends EntiteTenant {

    @NotBlank
    @Size(max = 60)
    @Column(name = "nom", nullable = false, length = 60)
    private String nom;

    @NotNull
    @Column(name = "date_greffage", nullable = false)
    private LocalDate dateGreffage;

    /** Reine dont on a greffé les larves, si elle est enregistrée. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "souche_id")
    private Reine souche;

    /** Colonie éleveuse, qui n'est presque jamais celle de la souche. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_eleveuse_id")
    private Ruche rucheEleveuse;

    @Pattern(regexp = "greffage|picking|cupularve|essaim_artificiel|autre")
    @Column(name = "methode", length = 20)
    private String methode;

    @Positive
    @Column(name = "nb_greffees", nullable = false)
    private Integer nbGreffees;

    @PositiveOrZero
    @Column(name = "nb_acceptees")
    private Integer nbAcceptees;

    @PositiveOrZero
    @Column(name = "nb_nees")
    private Integer nbNees;

    @PositiveOrZero
    @Column(name = "nb_fecondees")
    private Integer nbFecondees;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected SerieElevage() {
        // Requis par JPA.
    }

    public SerieElevage(String nom, LocalDate dateGreffage, Integer nbGreffees) {
        this.nom = nom;
        this.dateGreffage = dateGreffage;
        this.nbGreffees = nbGreffees;
    }

    /**
     * Taux d'acceptation, en pourcentage, ou {@code null} tant qu'il n'est pas
     * relevé.
     *
     * <p>Calculé, jamais stocké : le stocker créerait une valeur à maintenir en
     * cohérence avec deux colonnes voisines.
     */
    public Integer tauxAcceptation() {
        if (nbAcceptees == null || nbGreffees == null || nbGreffees == 0) {
            return null;
        }
        return Math.round(100f * nbAcceptees / nbGreffees);
    }

    /**
     * Taux de réussite d'ensemble : fécondées sur greffées.
     *
     * <p>C'est le seul chiffre qui compte vraiment pour un éleveur — une série
     * bien acceptée dont aucune reine ne revient de vol de fécondation n'a rien
     * produit.
     */
    public Integer tauxReussite() {
        if (nbFecondees == null || nbGreffees == null || nbGreffees == 0) {
            return null;
        }
        return Math.round(100f * nbFecondees / nbGreffees);
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public LocalDate getDateGreffage() {
        return dateGreffage;
    }

    public void setDateGreffage(LocalDate dateGreffage) {
        this.dateGreffage = dateGreffage;
    }

    public Reine getSouche() {
        return souche;
    }

    public void setSouche(Reine souche) {
        this.souche = souche;
    }

    public Ruche getRucheEleveuse() {
        return rucheEleveuse;
    }

    public void setRucheEleveuse(Ruche rucheEleveuse) {
        this.rucheEleveuse = rucheEleveuse;
    }

    public String getMethode() {
        return methode;
    }

    public void setMethode(String methode) {
        this.methode = methode;
    }

    public Integer getNbGreffees() {
        return nbGreffees;
    }

    public void setNbGreffees(Integer nbGreffees) {
        this.nbGreffees = nbGreffees;
    }

    public Integer getNbAcceptees() {
        return nbAcceptees;
    }

    public void setNbAcceptees(Integer nbAcceptees) {
        this.nbAcceptees = nbAcceptees;
    }

    public Integer getNbNees() {
        return nbNees;
    }

    public void setNbNees(Integer nbNees) {
        this.nbNees = nbNees;
    }

    public Integer getNbFecondees() {
        return nbFecondees;
    }

    public void setNbFecondees(Integer nbFecondees) {
        this.nbFecondees = nbFecondees;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
