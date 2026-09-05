package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Traitement sanitaire applique a une ruche (SPRINT-20).
 *
 * <p>Avant cette entite, {@code RaisonVisite.TRAITEMENT} disait qu'on avait
 * traite, et rien d'autre : ni le produit, ni la dose, ni la cible, ni le delai
 * de carence. C'est ce dernier qui bloquait tout registre sanitaire opposable —
 * sans lui, rien n'interdit de recolter du miel encore sous traitement.
 *
 * <p><strong>Valeurs contraintes par {@code @Pattern} et non par une
 * enumeration.</strong> C'est le choix deja fait pour {@link SuiviReine} : sept
 * jeux de valeurs sur les quatre entites de ce lot auraient demande autant
 * d'enumerations et d'{@code AttributeConverter}. Le CHECK en base reste la
 * garantie dure ; l'annotation la remonte au niveau du 400, avant l'aller-retour
 * SQL.
 */
@Entity
@Table(name = "traitement")
public class Traitement extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    /** Visite d'ou provient le traitement, si l'acte a ete saisi depuis un rapport. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visite_id")
    private Visite visite;

    @NotNull
    @Size(max = 120)
    @Column(name = "produit", nullable = false, length = 120)
    private String produit;

    @Size(max = 120)
    @Column(name = "substance_active", length = 120)
    private String substanceActive;

    @NotNull
    @Pattern(regexp = "varroa|loque_americaine|loque_europeenne|nosema"
            + "|petit_coleoptere|fausse_teigne|frelon|autre")
    @Column(name = "cible", nullable = false, length = 30)
    private String cible;

    @Positive
    @Column(name = "dose", precision = 10, scale = 3)
    private BigDecimal dose;

    @Pattern(regexp = "mg|g|ml|l|laniere|plaquette")
    @Column(name = "dose_unite", length = 10)
    private String doseUnite;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /** {@code null} tant que le traitement court : un traitement en cours est un etat normal. */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "delai_carence_jours")
    private Integer delaiCarenceJours;

    /**
     * Fin de carence, <strong>calculee par la base</strong>
     * ({@code date_fin + delai_carence_jours}).
     *
     * <p>Elle n'est ni ecrite ni modifiee par le code : {@code @Generated} demande
     * a Hibernate de la relire apres chaque insertion et mise a jour, faute de
     * quoi la reponse renvoyee juste apres une creation porterait {@code null}
     * pour la seule valeur que l'apiculteur est venu chercher.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "date_retrait", insertable = false, updatable = false)
    private LocalDate dateRetrait;

    @Size(max = 120)
    @Column(name = "ordonnance", length = 120)
    private String ordonnance;

    /**
     * Veterinaire signataire, et date de l'ordonnance (SPRINT-28).
     *
     * <p>La reference seule ne rendait rien verifiable : elle disait qu'une
     * ordonnance existe, jamais QUI l'a signee ni QUAND. Le document lui-meme
     * s'attache par {@code Photo.Cible.TRAITEMENT} — un scan est une image, et
     * le stockage existe depuis le SPRINT-21.
     *
     * <p>La base refuse une date sans reference ({@code ck_traitement_ordonnance}) ;
     * l'inverse reste permis, parce qu'une reference notee au rucher se complete
     * le soir.
     */
    @Size(max = 120)
    @Column(name = "ordonnance_veterinaire", length = 120)
    private String ordonnanceVeterinaire;

    @Column(name = "ordonnance_date")
    private LocalDate ordonnanceDate;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected Traitement() {
        // Requis par JPA.
    }

    public Traitement(Ruche ruche, Agent agent, String produit, String cible, LocalDate dateDebut) {
        this.ruche = ruche;
        this.agent = agent;
        this.produit = produit;
        this.cible = cible;
        this.dateDebut = dateDebut;
    }

    /**
     * Le miel de cette ruche est-il sous carence a la date donnee ?
     *
     * <p>Un traitement sans date de fin court toujours : la carence n'a pas
     * commence a s'ecouler, donc elle n'est pas echue. Repondre {@code false}
     * dans ce cas laisserait recolter pendant le traitement lui-meme.
     */
    public boolean sousCarence(LocalDate jour) {
        if (delaiCarenceJours == null) {
            return false;
        }
        if (dateFin == null) {
            return !jour.isBefore(dateDebut);
        }
        LocalDate fin = dateRetrait != null ? dateRetrait : dateFin.plusDays(delaiCarenceJours);
        return !jour.isBefore(dateDebut) && !jour.isAfter(fin);
    }

    public Ruche getRuche() {
        return ruche;
    }

    public Agent getAgent() {
        return agent;
    }

    public Visite getVisite() {
        return visite;
    }

    public void setVisite(Visite visite) {
        this.visite = visite;
    }

    public String getProduit() {
        return produit;
    }

    public void setProduit(String produit) {
        this.produit = produit;
    }

    public String getSubstanceActive() {
        return substanceActive;
    }

    public void setSubstanceActive(String substanceActive) {
        this.substanceActive = substanceActive;
    }

    public String getCible() {
        return cible;
    }

    public void setCible(String cible) {
        this.cible = cible;
    }

    public BigDecimal getDose() {
        return dose;
    }

    public void setDose(BigDecimal dose) {
        this.dose = dose;
    }

    public String getDoseUnite() {
        return doseUnite;
    }

    public void setDoseUnite(String doseUnite) {
        this.doseUnite = doseUnite;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public Integer getDelaiCarenceJours() {
        return delaiCarenceJours;
    }

    public void setDelaiCarenceJours(Integer delaiCarenceJours) {
        this.delaiCarenceJours = delaiCarenceJours;
    }

    public LocalDate getDateRetrait() {
        return dateRetrait;
    }

    public String getOrdonnance() {
        return ordonnance;
    }

    public void setOrdonnance(String ordonnance) {
        this.ordonnance = ordonnance;
    }

    public String getOrdonnanceVeterinaire() {
        return ordonnanceVeterinaire;
    }

    public void setOrdonnanceVeterinaire(String ordonnanceVeterinaire) {
        this.ordonnanceVeterinaire = ordonnanceVeterinaire;
    }

    public LocalDate getOrdonnanceDate() {
        return ordonnanceDate;
    }

    public void setOrdonnanceDate(LocalDate ordonnanceDate) {
        this.ordonnanceDate = ordonnanceDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
