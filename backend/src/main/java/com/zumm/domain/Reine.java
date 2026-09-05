package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * La reine comme objet, avec sa filiation (SPRINT-29, lot D).
 *
 * <p>Ferme les lignes « généalogie / arbre de lignées », « ailes clippées,
 * fournisseur, ruche-mère » et « dates de greffage » du §7 de
 * {@code docs/ECART-CONCURRENTS.md}.
 *
 * <p><strong>À ne pas confondre avec {@link SuiviReine}</strong>, et c'est la
 * décision structurante du lot. {@code SuiviReine} est le <em>journal d'une
 * ruche</em> depuis le SPRINT-07 : une ligne y est un événement — introduite, en
 * ponte, remplacée —, et une même ruche en porte des dizaines, appartenant à des
 * reines successives.
 *
 * <p>Le plan de couverture annonçait « une clé étrangère réflexive sur
 * {@code suivi_reine} » : elle aurait relié des <strong>événements</strong>. La
 * question « de quelle mère descend cette reine ? » n'aurait eu aucune réponse
 * stable — quel événement désigne la mère, celui de son introduction ou celui de
 * sa disparition ? L'arbre aurait dépendu de la façon dont on l'a construit.
 *
 * <p>D'où cette table. {@code SuiviReine} reste le journal, et gagne un
 * {@code reine_id} facultatif : les événements antérieurs au SPRINT-29 ne
 * désignent aucune reine, et deviner laquelle aurait fabriqué une généalogie.
 */
@Entity
@Table(name = "reine")
public class Reine extends EntiteTenant {

    @Size(max = 40)
    @Column(name = "code", length = 40)
    private String code;

    /**
     * Reine mère. Réflexive, et détachée plutôt qu'effacée en cascade :
     * supprimer une aïeule ne doit pas emporter sa descendance, qui existe bel
     * et bien dans le rucher.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mere_id")
    private Reine mere;

    /**
     * Colonie dont on a greffé les larves, distincte de {@link #mere}.
     *
     * <p>On greffe souvent depuis une ruche dont la reine n'est pas
     * enregistrée : sans cette colonne, la seule trace de la souche serait
     * perdue.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_mere_id")
    private Ruche rucheMere;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serie_id")
    private SerieElevage serie;

    /** Ruche où la reine est en service. {@code null} : nucleus, banque, vendue. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_id")
    private Ruche ruche;

    @Pattern(regexp = "elevage|achat|essaimage|supersedure|inconnue")
    @Column(name = "origine", nullable = false, length = 20)
    private String origine = "inconnue";

    @Size(max = 120)
    @Column(name = "fournisseur", length = 120)
    private String fournisseur;

    @Size(max = 60)
    @Column(name = "race", length = 60)
    private String race;

    @Column(name = "annee_naissance")
    private Integer anneeNaissance;

    @Pattern(regexp = "blanc|jaune|rouge|vert|bleu")
    @Column(name = "couleur_marquage", length = 10)
    private String couleurMarquage;

    /**
     * Ailes clippées. {@code Boolean} et non {@code boolean} : « on ne sait
     * pas » n'est pas « non clippée », et une reine achetée arrive souvent sans
     * qu'on l'ait vérifié.
     */
    @Column(name = "ailes_clippees")
    private Boolean ailesClippees;

    @Column(name = "date_greffage")
    private LocalDate dateGreffage;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "date_fecondation")
    private LocalDate dateFecondation;

    @Column(name = "date_introduction")
    private LocalDate dateIntroduction;

    /**
     * Fin de règne.
     *
     * <p>Elle borne l'index génétique : n'attribuer à une reine que ce qui s'est
     * passé pendant son règne, sur sa ruche, est ce qui rend la comparaison
     * honnête. Sans cette date, une reine introduite en juillet hériterait de la
     * récolte de printemps de la précédente.
     */
    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Pattern(regexp = "en_service|reserve|remplacee|disparue|morte|vendue")
    @Column(name = "statut", nullable = false, length = 20)
    private String statut = "en_service";

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected Reine() {
        // Requis par JPA.
    }

    public Reine(String origine) {
        this.origine = origine;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Reine getMere() {
        return mere;
    }

    public void setMere(Reine mere) {
        this.mere = mere;
    }

    public Ruche getRucheMere() {
        return rucheMere;
    }

    public void setRucheMere(Ruche rucheMere) {
        this.rucheMere = rucheMere;
    }

    public SerieElevage getSerie() {
        return serie;
    }

    public void setSerie(SerieElevage serie) {
        this.serie = serie;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public void setRuche(Ruche ruche) {
        this.ruche = ruche;
    }

    public String getOrigine() {
        return origine;
    }

    public void setOrigine(String origine) {
        this.origine = origine;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public void setFournisseur(String fournisseur) {
        this.fournisseur = fournisseur;
    }

    public String getRace() {
        return race;
    }

    public void setRace(String race) {
        this.race = race;
    }

    public Integer getAnneeNaissance() {
        return anneeNaissance;
    }

    public void setAnneeNaissance(Integer anneeNaissance) {
        this.anneeNaissance = anneeNaissance;
    }

    public String getCouleurMarquage() {
        return couleurMarquage;
    }

    public void setCouleurMarquage(String couleurMarquage) {
        this.couleurMarquage = couleurMarquage;
    }

    public Boolean getAilesClippees() {
        return ailesClippees;
    }

    public void setAilesClippees(Boolean ailesClippees) {
        this.ailesClippees = ailesClippees;
    }

    public LocalDate getDateGreffage() {
        return dateGreffage;
    }

    public void setDateGreffage(LocalDate dateGreffage) {
        this.dateGreffage = dateGreffage;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public LocalDate getDateFecondation() {
        return dateFecondation;
    }

    public void setDateFecondation(LocalDate dateFecondation) {
        this.dateFecondation = dateFecondation;
    }

    public LocalDate getDateIntroduction() {
        return dateIntroduction;
    }

    public void setDateIntroduction(LocalDate dateIntroduction) {
        this.dateIntroduction = dateIntroduction;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
