package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Un équipement de l'exploitation (SPRINT-27, lot E).
 *
 * <p>Ferme deux lignes à la fois : « inventaire du matériel et état
 * d'entretien » (§6) et « plan de maintenance : tâches récurrentes attachées à
 * un équipement, pas à une ruche » (§13, déduit d'un conseil d'Onibi —
 * « nettoyez les optiques, grattez la propolis sur les glissières »).
 *
 * <p><strong>La prochaine échéance n'est pas stockée.</strong> Elle se calcule :
 * {@code derniereMaintenance + periodiciteJours}. La ranger en base créerait une
 * valeur à maintenir en cohérence avec la dernière maintenance — exactement la
 * dette que {@code ComptageVarroaService} évite pour le taux de varroa. C'est
 * {@code RegleMaintenanceMateriel} qui la déduit, au passage du moteur.
 */
@Entity
@Table(name = "materiel")
public class Materiel extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "libelle", nullable = false, length = 120)
    private String libelle;

    @NotNull
    @Pattern(regexp = "ruche|hausse|cadre|extracteur|maturateur|enfumoir"
            + "|protection|vehicule|balance|autre")
    @Column(name = "categorie", nullable = false, length = 20)
    private String categorie;

    @Min(1)
    @Column(name = "quantite", nullable = false)
    private int quantite = 1;

    /**
     * Où il se trouve. FACULTATIF : un extracteur vit à la miellerie, qui n'est
     * pas un rucher, et exiger un site ferait inventer des ruchers fictifs.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @NotNull
    @Pattern(regexp = "neuf|bon|a_reviser|hors_service")
    @Column(name = "etat", nullable = false, length = 20)
    private String etat = "bon";

    /**
     * Périodicité d'entretien, en jours.
     *
     * <p>NULLE = matériel qui ne s'entretient PAS (une hausse vide), et non
     * « à entretenir quand on y pense ». La distinction compte : le moteur de
     * règles ignore ce cas et ne propose donc rien, ce qui est juste.
     */
    @Column(name = "periodicite_jours")
    private Integer periodiciteJours;

    @Column(name = "derniere_maintenance")
    private LocalDate derniereMaintenance;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected Materiel() {
        // Requis par JPA.
    }

    public Materiel(String libelle, String categorie, int quantite) {
        this.libelle = libelle;
        this.categorie = categorie;
        this.quantite = quantite;
    }

    /**
     * Date à laquelle l'entretien est dû, ou {@code null} s'il n'y a pas de
     * périodicité.
     *
     * <p>Un matériel jamais entretenu est dû <strong>le jour donné</strong> :
     * partir de sa date de création serait plus fin, mais un extracteur saisi
     * l'hiver dernier et jamais révisé doit apparaître, pas attendre un an.
     */
    public LocalDate prochaineMaintenance(LocalDate jour) {
        if (periodiciteJours == null) {
            return null;
        }
        return derniereMaintenance == null ? jour : derniereMaintenance.plusDays(periodiciteJours);
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

    public int getQuantite() {
        return quantite;
    }

    public void setQuantite(int quantite) {
        this.quantite = quantite;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getEtat() {
        return etat;
    }

    public void setEtat(String etat) {
        this.etat = etat;
    }

    public Integer getPeriodiciteJours() {
        return periodiciteJours;
    }

    public void setPeriodiciteJours(Integer periodiciteJours) {
        this.periodiciteJours = periodiciteJours;
    }

    public LocalDate getDerniereMaintenance() {
        return derniereMaintenance;
    }

    public void setDerniereMaintenance(LocalDate derniereMaintenance) {
        this.derniereMaintenance = derniereMaintenance;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
