package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Visite realisee et rapport (US-009). Peut decouler d'un planning approuve
 * (optionnel). Le rapport regroupe constatations, actions et evaluation de
 * l'essaim.
 */
@Entity
@Table(name = "visite")
public class Visite extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planning_id")
    private Planning planning;

    @NotNull
    @Column(name = "date_visite", nullable = false)
    private LocalDate dateVisite;

    @Column(name = "heure_visite")
    private LocalTime heureVisite;

    @Column(name = "duree_min")
    private Integer dureeMin;

    @NotNull
    @Column(name = "raison", nullable = false, length = 20)
    private RaisonVisite raison = RaisonVisite.CONTROLE;

    @Column(name = "constatations", columnDefinition = "text")
    private String constatations;

    @Column(name = "actions_prevues", columnDefinition = "text")
    private String actionsPrevues;

    @Column(name = "actions_effectuees", columnDefinition = "text")
    private String actionsEffectuees;

    @Column(name = "recommandations", columnDefinition = "text")
    private String recommandations;

    @Column(name = "effectif_qualitatif", length = 10)
    private EffectifQualitatif effectifQualitatif;

    @Column(name = "etat_sante", length = 10)
    private EtatSante etatSante;

    @Min(1)
    @Max(3)
    @Column(name = "productivite")
    private Integer productivite;

    protected Visite() {
        // Requis par JPA.
    }

    public Visite(Ruche ruche, Agent agent, LocalDate dateVisite, RaisonVisite raison) {
        this.ruche = ruche;
        this.agent = agent;
        this.dateVisite = dateVisite;
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

    public Planning getPlanning() {
        return planning;
    }

    public void setPlanning(Planning planning) {
        this.planning = planning;
    }

    public LocalDate getDateVisite() {
        return dateVisite;
    }

    public void setDateVisite(LocalDate dateVisite) {
        this.dateVisite = dateVisite;
    }

    public LocalTime getHeureVisite() {
        return heureVisite;
    }

    public void setHeureVisite(LocalTime heureVisite) {
        this.heureVisite = heureVisite;
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

    public String getConstatations() {
        return constatations;
    }

    public void setConstatations(String constatations) {
        this.constatations = constatations;
    }

    public String getActionsPrevues() {
        return actionsPrevues;
    }

    public void setActionsPrevues(String actionsPrevues) {
        this.actionsPrevues = actionsPrevues;
    }

    public String getActionsEffectuees() {
        return actionsEffectuees;
    }

    public void setActionsEffectuees(String actionsEffectuees) {
        this.actionsEffectuees = actionsEffectuees;
    }

    public String getRecommandations() {
        return recommandations;
    }

    public void setRecommandations(String recommandations) {
        this.recommandations = recommandations;
    }

    public EffectifQualitatif getEffectifQualitatif() {
        return effectifQualitatif;
    }

    public void setEffectifQualitatif(EffectifQualitatif effectifQualitatif) {
        this.effectifQualitatif = effectifQualitatif;
    }

    public EtatSante getEtatSante() {
        return etatSante;
    }

    public void setEtatSante(EtatSante etatSante) {
        this.etatSante = etatSante;
    }

    public Integer getProductivite() {
        return productivite;
    }

    public void setProductivite(Integer productivite) {
        this.productivite = productivite;
    }
}
