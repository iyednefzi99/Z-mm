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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une dépense de l'exploitation (SPRINT-27, lot E).
 *
 * <p>Ferme le 🟡 du §6 : « `SyntheseService` calcule un ROI global depuis
 * `ConfigZumm.ini` ; pas de coûts réels, pas de ventilation par ruche ou par
 * rucher ». Le ROI reposait sur une hypothèse de coût par visite ; il repose
 * désormais sur ce qui a réellement été dépensé.
 *
 * <p><strong>La frontière est ici, et elle est nette.</strong> Cette entité
 * porte un montant, une date, une catégorie et une affectation facultative. Elle
 * ne porte NI fournisseur, NI numéro de pièce, NI TVA, NI échéance de paiement.
 * Chacune de ces colonnes appellerait la suivante, et la troisième rendrait le
 * module obligatoire pour boucler un exercice — alors que personne n'a demandé à
 * Zümm de tenir des comptes. Facturation, TVA, devis et clients restent ⛔ (§9).
 *
 * <p><strong>Le montant ne peut pas être négatif.</strong> Une dépense négative
 * est une recette, et les recettes se calculent depuis les récoltes. Les
 * mélanger rendrait tout total ambigu — et un bilan dont on ne sait pas ce qu'il
 * additionne ne vaut rien.
 */
@Entity
@Table(name = "depense")
public class Depense extends EntiteTenant {

    @NotBlank
    @Size(max = 160)
    @Column(name = "libelle", nullable = false, length = 160)
    private String libelle;

    @NotNull
    @Pattern(regexp = "materiel|consommable|traitement|nourrissement|cheptel"
            + "|transport|analyse|assurance|formation|autre")
    @Column(name = "categorie", nullable = false, length = 20)
    private String categorie;

    @NotNull
    @PositiveOrZero
    @Column(name = "montant_eur", nullable = false, precision = 10, scale = 2)
    private BigDecimal montantEur;

    @NotNull
    @Column(name = "date_depense", nullable = false)
    private LocalDate dateDepense;

    /**
     * Affectation FACULTATIVE, à deux niveaux.
     *
     * <p>Une dépense se rattache à une ruche (une reine achetée), à un rucher
     * (un transport de transhumance), ou à rien du tout (une assurance). Exiger
     * une affectation ferait inventer des rattachements pour boucler une saisie,
     * et la rentabilité par ruche en deviendrait fausse — pas incomplète,
     * fausse.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_id")
    private Ruche ruche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected Depense() {
        // Requis par JPA.
    }

    public Depense(String libelle, String categorie, BigDecimal montantEur,
            LocalDate dateDepense) {
        this.libelle = libelle;
        this.categorie = categorie;
        this.montantEur = montantEur;
        this.dateDepense = dateDepense;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public BigDecimal getMontantEur() {
        return montantEur;
    }

    public void setMontantEur(BigDecimal montantEur) {
        this.montantEur = montantEur;
    }

    public LocalDate getDateDepense() {
        return dateDepense;
    }

    public void setDateDepense(LocalDate dateDepense) {
        this.dateDepense = dateDepense;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public void setRuche(Ruche ruche) {
        this.ruche = ruche;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
