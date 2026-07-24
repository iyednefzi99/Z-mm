package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Planning de visite (US-007, US-008). Un agent planifie la visite d'une ruche ;
 * un superviseur l'approuve ou la refuse (avec motif).
 */
@Entity
@Table(name = "planning")
public class Planning extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superviseur_id")
    private Agent superviseur;

    @NotNull
    @Column(name = "date_prevue", nullable = false)
    private LocalDate datePrevue;

    @Column(name = "heure_prevue")
    private LocalTime heurePrevue;

    @Column(name = "duree_min")
    private Integer dureeMin;

    @NotNull
    @Column(name = "raison", nullable = false, length = 20)
    private RaisonVisite raison = RaisonVisite.CONTROLE;

    @NotNull
    @Column(name = "statut", nullable = false, length = 20)
    private StatutPlanning statut = StatutPlanning.PROPOSE;

    @Column(name = "motif_refus")
    private String motifRefus;

    protected Planning() {
        // Requis par JPA.
    }

    public Planning(Ruche ruche, Agent agent, LocalDate datePrevue, RaisonVisite raison) {
        this.ruche = ruche;
        this.agent = agent;
        this.datePrevue = datePrevue;
        this.raison = raison;
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

    public Agent getSuperviseur() {
        return superviseur;
    }

    public void setSuperviseur(Agent superviseur) {
        this.superviseur = superviseur;
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

    public Integer getDureeMin() {
        return dureeMin;
    }

    public void setDureeMin(Integer dureeMin) {
        this.dureeMin = dureeMin;
    }

    public RaisonVisite getRaison() {
        return raison;
    }

    public void setRaison(RaisonVisite raison) {
        this.raison = raison;
    }

    public StatutPlanning getStatut() {
        return statut;
    }

    public void setStatut(StatutPlanning statut) {
        this.statut = statut;
    }

    public String getMotifRefus() {
        return motifRefus;
    }

    public void setMotifRefus(String motifRefus) {
        this.motifRefus = motifRefus;
    }
}
