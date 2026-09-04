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
 * Capture d'essaim (SPRINT-21, §1 de {@code docs/ECART-CONCURRENTS.md}).
 *
 * <p>L'autre porte d'entree d'une colonie, et la seule qui ne coute rien.
 * HiveBook l'enregistre au meme rang qu'une division ; Zumm ne la nommait nulle
 * part.
 *
 * <p><strong>Sans coordonnees, deliberement.</strong> Une colonne de position est
 * une surface de fuite de plus a filtrer (invariant {@code PolitiquePositions}),
 * pour une donnee dont personne ne fait rien : le lieu de capture d'un essaim ne
 * se cartographie pas, il se raconte — « haie du voisin, chemin des Vignes ».
 * {@link #lieu} le dit mieux et ne se trilatere pas.
 */
@Entity
@Table(name = "capture_essaim")
public class CaptureEssaim extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    /**
     * Ruche dans laquelle l'essaim a ete loge. Nulle tant qu'il est en ruchette
     * d'attente : une capture existe avant d'avoir une ruche.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_id")
    private Ruche ruche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @NotNull
    @Column(name = "date_capture", nullable = false)
    private LocalDate dateCapture;

    @NotNull
    @Pattern(regexp = "essaim_naturel|piege|recuperation|signalement|autre")
    @Column(name = "origine", nullable = false, length = 20)
    private String origine;

    /** Repere humain, pas un point sur une carte. */
    @Size(max = 200)
    @Column(name = "lieu", length = 200)
    private String lieu;

    @DecimalMin("0.0")
    @DecimalMax("20.0")
    @Column(name = "poids_kg", precision = 5, scale = 2)
    private BigDecimal poidsKg;

    /** Hauteur de la capture : elle dit le materiel qu'il a fallu, et le risque pris. */
    @DecimalMin("0.0")
    @DecimalMax("60.0")
    @Column(name = "hauteur_m", precision = 4, scale = 1)
    private BigDecimal hauteurM;

    @Size(max = 2000)
    @Column(name = "note")
    private String note;

    protected CaptureEssaim() {
        // Requis par JPA.
    }

    public CaptureEssaim(Agent agent, LocalDate dateCapture, String origine) {
        this.agent = agent;
        this.dateCapture = dateCapture;
        this.origine = origine;
    }

    public Agent getAgent() {
        return agent;
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

    public LocalDate getDateCapture() {
        return dateCapture;
    }

    public String getOrigine() {
        return origine;
    }

    public String getLieu() {
        return lieu;
    }

    public void setLieu(String lieu) {
        this.lieu = lieu;
    }

    public BigDecimal getPoidsKg() {
        return poidsKg;
    }

    public void setPoidsKg(BigDecimal poidsKg) {
        this.poidsKg = poidsKg;
    }

    public BigDecimal getHauteurM() {
        return hauteurM;
    }

    public void setHauteurM(BigDecimal hauteurM) {
        this.hauteurM = hauteurM;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
