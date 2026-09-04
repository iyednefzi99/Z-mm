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
import jakarta.validation.constraints.Size;

/**
 * Ressource florale declaree autour d'un rucher (SPRINT-21, §1 de
 * {@code docs/ECART-CONCURRENTS.md}).
 *
 * <p>Table fille plutot que colonne texte, pour la raison qui a fait naitre
 * {@link ObservationPathologie} : une liste NOMMEE se compte, se filtre et se
 * traduit ; un champ libre « acacia, chataignier » ne fait aucune des trois.
 *
 * <p><strong>Elle dit QUOI, jamais QUAND.</strong> Le calendrier de floraison
 * releve de l'ecart 5 du §11, qui suppose de trancher d'abord un referentiel
 * geographique portable hors de France — decision produit que cette entite n'a
 * pas a preempter.
 */
@Entity
@Table(name = "ressource_florale")
public class RessourceFlorale extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    /**
     * Vocabulaire volontairement mixte — colza et jujubier, chataignier et
     * palmier dattier : il suit le marche que vise le trilinguisme FR/EN/AR. Un
     * referentiel purement metropolitain aurait reproduit le defaut de
     * portabilite reproche a BeeGIS au §2 du meme document.
     */
    @NotNull
    @Pattern(regexp = "colza|tournesol|acacia|chataignier|tilleul|lavande|bruyere|luzerne"
            + "|sarrasin|verger|agrumes|eucalyptus|thym|romarin|jujubier|palmier_dattier"
            + "|prairie|foret|garrigue|autre")
    @Column(name = "ressource", nullable = false, length = 20)
    private String ressource;

    /**
     * Distance approximative au rucher, en metres. Bornee a 20 km : au-dela du
     * rayon de butinage le plus genereux, la ressource n'est plus une ressource,
     * c'est un paysage.
     */
    @Min(0)
    @Max(20_000)
    @Column(name = "distance_m")
    private Integer distanceM;

    /**
     * Fenetre de floraison, en MOIS (1-12) : la « miellee » du §1.
     *
     * <p>Des mois et non des dates, parce qu'une floraison revient chaque annee.
     * Une date la figerait a un millesime et obligerait a ressaisir les memes
     * lignes tous les ans — ce qui, en pratique, veut dire qu'elles ne seraient
     * jamais ressaisies.
     *
     * <p>La fenetre peut ENJAMBER l'annee : l'eucalyptus du Sud fleurit de
     * novembre a fevrier, donc {@code moisFin < moisDebut} est valide et se lit
     * modulo douze ({@link #enFloraison(int)}).
     */
    @Min(1)
    @Max(12)
    @Column(name = "mois_debut")
    private Integer moisDebut;

    @Min(1)
    @Max(12)
    @Column(name = "mois_fin")
    private Integer moisFin;

    @Size(max = 2000)
    @Column(name = "note")
    private String note;

    protected RessourceFlorale() {
        // Requis par JPA.
    }

    public RessourceFlorale(String ressource, Integer distanceM, String note) {
        this.ressource = ressource;
        this.distanceM = distanceM;
        this.note = note;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getRessource() {
        return ressource;
    }

    public void setRessource(String ressource) {
        this.ressource = ressource;
    }

    public Integer getDistanceM() {
        return distanceM;
    }

    public void setDistanceM(Integer distanceM) {
        this.distanceM = distanceM;
    }

    /**
     * Cette ressource fleurit-elle au mois donne ?
     *
     * <p>Le cas qui compte est celui de la fenetre a cheval sur l'annee : de
     * novembre (11) a fevrier (2), decembre en fait partie et juin non. Un simple
     * `entre debut et fin` repondrait l'inverse.
     */
    public boolean enFloraison(int mois) {
        if (moisDebut == null || moisFin == null) {
            return false;
        }
        return moisDebut <= moisFin
                ? mois >= moisDebut && mois <= moisFin
                : mois >= moisDebut || mois <= moisFin;
    }

    public Integer getMoisDebut() {
        return moisDebut;
    }

    public void setMoisDebut(Integer moisDebut) {
        this.moisDebut = moisDebut;
    }

    public Integer getMoisFin() {
        return moisFin;
    }

    public void setMoisFin(Integer moisFin) {
        this.moisFin = moisFin;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
