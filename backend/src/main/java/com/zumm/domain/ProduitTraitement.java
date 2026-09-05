package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Produit de traitement du referentiel pre-renseigne (SPRINT-28, lot I).
 *
 * <p>Ferme la ligne « referentiel de traitements pre-renseigne » du §3.
 * Meme regime que {@link PointObservation} : ferme, sans tenant, en lecture
 * seule pour l'application (la V28 lui retire le DML).
 *
 * <p><strong>Ce qu'il est, et ce qu'il n'est pas.</strong> Il pre-remplit un
 * formulaire ; il ne fait pas autorite. La notice et l'autorisation de mise sur
 * le marche du pays d'exercice font foi — d'ou {@code mention}, affichee telle
 * quelle a la saisie. Et le traitement enregistre garde sa <strong>propre</strong>
 * copie du delai ({@code Traitement.delaiCarenceJours}, SPRINT-20) : corriger le
 * referentiel demain ne doit pas reecrire un registre d'elevage d'hier, qui est
 * un document opposable.
 *
 * <p><strong>Ce que le seul delai en jours ne dit pas.</strong> Sur la plupart
 * des varroacides, la contrainte reelle n'est pas une carence mais « hausses
 * retirees » : un delai de zero jour, lu seul, se comprend comme « on peut
 * recolter ». D'ou {@link #isHaussesRetirees()}, une colonne et non une phrase
 * perdue dans une note.
 */
@Entity
@Table(name = "produit_traitement")
@Immutable
public class ProduitTraitement {

    @Id
    @Column(name = "code", nullable = false, length = 40, updatable = false)
    private String code;

    @Column(name = "nom", nullable = false, length = 80, updatable = false)
    private String nom;

    @Column(name = "substance_active", nullable = false, length = 120, updatable = false)
    private String substanceActive;

    @Column(name = "cible", nullable = false, length = 30, updatable = false)
    private String cible;

    @Column(name = "forme", nullable = false, length = 20, updatable = false)
    private String forme;

    @Column(name = "delai_carence_jours", nullable = false, updatable = false)
    private Integer delaiCarenceJours;

    @Column(name = "hausses_retirees", nullable = false, updatable = false)
    private boolean haussesRetirees;

    @Column(name = "ordonnance_requise", nullable = false, updatable = false)
    private boolean ordonnanceRequise;

    @Column(name = "mention", length = 200, updatable = false)
    private String mention;

    protected ProduitTraitement() {
        // Requis par JPA. Cette table ne s'ecrit que par migration.
    }

    public String getCode() {
        return code;
    }

    public String getNom() {
        return nom;
    }

    public String getSubstanceActive() {
        return substanceActive;
    }

    public String getCible() {
        return cible;
    }

    public String getForme() {
        return forme;
    }

    public Integer getDelaiCarenceJours() {
        return delaiCarenceJours;
    }

    public boolean isHaussesRetirees() {
        return haussesRetirees;
    }

    public boolean isOrdonnanceRequise() {
        return ordonnanceRequise;
    }

    public String getMention() {
        return mention;
    }
}
