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
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
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

    // ─── Observations structurees (SPRINT-20) ───────────────────────────────
    //
    // Ces champs sortent de `constatations` ce qui doit pouvoir se COMPTER. Le
    // texte libre reste — il porte ce qu'aucune case ne prevoit — mais il cesse
    // d'etre la seule trace du couvain, des reserves et des cellules royales,
    // ou rien n'etait analysable.
    //
    // Tous nullables : une visite eclair (« poser une hausse ») ne remplit rien,
    // et exiger la grille complete ferait sauter la saisie plutot que la
    // completer.

    /** Presence d'oeufs : le signe le plus sur d'une reine pondeuse recente. */
    @Column(name = "couvain_oeufs")
    private Boolean couvainOeufs;

    @Column(name = "couvain_larves")
    private Boolean couvainLarves;

    @Column(name = "couvain_opercule")
    private Boolean couvainOpercule;

    /** Motif de ponte : compact, lacunaire, irregulier, absent. */
    @Pattern(regexp = "compact|lacunaire|irregulier|absent")
    @Column(name = "motif_ponte", length = 15)
    private String motifPonte;

    /**
     * Reine vue pendant CETTE visite.
     *
     * <p>Distinct de {@link SuiviReine}, qui est le journal des evenements de la
     * reine : ici c'est une case du formulaire, la ou le journal enregistre une
     * introduction ou un remplacement. Les carnets concurrents cochent, Zumm ne
     * savait qu'evenementialiser — les deux sont utiles, ils ne repondent pas a
     * la meme question.
     */
    @Column(name = "reine_vue")
    private Boolean reineVue;

    @Min(0)
    @Column(name = "cellules_royales")
    private Integer cellulesRoyales;

    /**
     * Pourquoi ces cellules : essaimage, supersedure, urgence.
     *
     * <p>C'est la donnee actionnable, pas le nombre : trois cellules de
     * supersedure se laissent faire, trois cellules d'essaimage demandent une
     * division dans la semaine.
     */
    @Pattern(regexp = "essaimage|supersedure|urgence")
    @Column(name = "cellules_royales_cause", length = 15)
    private String cellulesRoyalesCause;

    @Min(0)
    @Max(40)
    @Column(name = "cadres_couvain")
    private Integer cadresCouvain;

    @Min(0)
    @Max(40)
    @Column(name = "cadres_miel")
    private Integer cadresMiel;

    @Min(0)
    @Max(40)
    @Column(name = "cadres_pollen")
    private Integer cadresPollen;

    @Pattern(regexp = "doux|normal|agressif")
    @Column(name = "temperament", length = 10)
    private String temperament;

    // ─── Meteo FIGEE au moment de la visite (SPRINT-20) ─────────────────────
    //
    // Recopiee du fournisseur a la saisie, jamais rappelee ensuite. Une
    // prevision se revise, un releve non — et c'est le releve qui permet de
    // correler conditions et production. Aller rechercher la meteo du 12 mars
    // six mois plus tard rendrait la correlation fausse : on obtiendrait la
    // valeur reconstituee d'aujourd'hui, pas celle qui a ete observee.

    @Column(name = "meteo_temperature_c", precision = 4, scale = 1)
    private BigDecimal meteoTemperatureC;

    @Min(0)
    @Max(100)
    @Column(name = "meteo_humidite_pct")
    private Integer meteoHumiditePct;

    @Column(name = "meteo_vent_kmh", precision = 5, scale = 1)
    private BigDecimal meteoVentKmh;

    /** D'ou vient le releve : {@code open-meteo}, {@code simulation} ou {@code saisie}. */
    @Pattern(regexp = "open-meteo|simulation|saisie")
    @Column(name = "meteo_source", length = 20)
    private String meteoSource;

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

    public Boolean getCouvainOeufs() {
        return couvainOeufs;
    }

    public void setCouvainOeufs(Boolean couvainOeufs) {
        this.couvainOeufs = couvainOeufs;
    }

    public Boolean getCouvainLarves() {
        return couvainLarves;
    }

    public void setCouvainLarves(Boolean couvainLarves) {
        this.couvainLarves = couvainLarves;
    }

    public Boolean getCouvainOpercule() {
        return couvainOpercule;
    }

    public void setCouvainOpercule(Boolean couvainOpercule) {
        this.couvainOpercule = couvainOpercule;
    }

    public String getMotifPonte() {
        return motifPonte;
    }

    public void setMotifPonte(String motifPonte) {
        this.motifPonte = motifPonte;
    }

    public Boolean getReineVue() {
        return reineVue;
    }

    public void setReineVue(Boolean reineVue) {
        this.reineVue = reineVue;
    }

    public Integer getCellulesRoyales() {
        return cellulesRoyales;
    }

    public void setCellulesRoyales(Integer cellulesRoyales) {
        this.cellulesRoyales = cellulesRoyales;
    }

    public String getCellulesRoyalesCause() {
        return cellulesRoyalesCause;
    }

    public void setCellulesRoyalesCause(String cellulesRoyalesCause) {
        this.cellulesRoyalesCause = cellulesRoyalesCause;
    }

    public Integer getCadresCouvain() {
        return cadresCouvain;
    }

    public void setCadresCouvain(Integer cadresCouvain) {
        this.cadresCouvain = cadresCouvain;
    }

    public Integer getCadresMiel() {
        return cadresMiel;
    }

    public void setCadresMiel(Integer cadresMiel) {
        this.cadresMiel = cadresMiel;
    }

    public Integer getCadresPollen() {
        return cadresPollen;
    }

    public void setCadresPollen(Integer cadresPollen) {
        this.cadresPollen = cadresPollen;
    }

    public String getTemperament() {
        return temperament;
    }

    public void setTemperament(String temperament) {
        this.temperament = temperament;
    }

    public BigDecimal getMeteoTemperatureC() {
        return meteoTemperatureC;
    }

    public void setMeteoTemperatureC(BigDecimal meteoTemperatureC) {
        this.meteoTemperatureC = meteoTemperatureC;
    }

    public Integer getMeteoHumiditePct() {
        return meteoHumiditePct;
    }

    public void setMeteoHumiditePct(Integer meteoHumiditePct) {
        this.meteoHumiditePct = meteoHumiditePct;
    }

    public BigDecimal getMeteoVentKmh() {
        return meteoVentKmh;
    }

    public void setMeteoVentKmh(BigDecimal meteoVentKmh) {
        this.meteoVentKmh = meteoVentKmh;
    }

    public String getMeteoSource() {
        return meteoSource;
    }

    public void setMeteoSource(String meteoSource) {
        this.meteoSource = meteoSource;
    }
}
