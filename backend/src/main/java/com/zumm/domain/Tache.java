package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Tache ou rappel de l'apiculteur (US-031).
 *
 * <p>Optionnellement rattachee a une ruche et assignee a un agent, avec une
 * echeance qui alimente le calendrier des rappels. Multi-tenant par
 * {@code tenant_id} + RLS, comme les autres entites (cf. migration V7).
 */
@Entity
@Table(name = "tache")
public class Tache extends EntiteTenant {

    @NotBlank
    @Size(max = 200)
    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_id")
    private Ruche ruche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id")
    private Agent agent;

    @Column(name = "echeance")
    private LocalDate echeance;

    @Column(name = "faite", nullable = false)
    private boolean faite;

    protected Tache() {
        // Requis par JPA.
    }

    public Tache(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public void setRuche(Ruche ruche) {
        this.ruche = ruche;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }

    public LocalDate getEcheance() {
        return echeance;
    }

    public void setEcheance(LocalDate echeance) {
        this.echeance = echeance;
    }

    public boolean isFaite() {
        return faite;
    }

    public void setFaite(boolean faite) {
        this.faite = faite;
    }
}
