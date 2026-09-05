package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Gabarit d'inspection : le carnet, tel qu'une exploitation le veut (SPRINT-28).
 *
 * <p>Le SPRINT-20 avait structure la grille d'inspection ; il l'avait aussi
 * figee — onze colonnes, les memes pour tout le monde. Un gabarit choisit les
 * points du referentiel ferme ({@link PointObservation}) qui figureront a la
 * saisie, et allume ou eteint les quatre sections du noyau.
 *
 * <p><strong>Le noyau reste des colonnes.</strong> Les onze champs de la V19 ne
 * migrent pas dans le referentiel : ils sont types, indexes, et lus par le
 * moteur de regles, les indices de colonie et la fiche d'inspection. Les
 * transformer en lignes cle-valeur aurait casse tout cela pour une uniformite
 * dont personne n'a l'usage. Le gabarit les MASQUE ; il ne les deplace pas, et
 * une visite deja saisie garde ce qu'elle portait.
 *
 * <p>Un gabarit se desactive plutot qu'il ne se supprime des qu'il a servi :
 * les visites qui s'en sont servies restent lisibles, et leur gabarit reste
 * nommable.
 */
@Entity
@Table(name = "gabarit_inspection")
public class GabaritInspection extends EntiteTenant {

    @NotBlank
    @Size(max = 60)
    @Column(name = "nom", nullable = false, length = 60)
    private String nom;

    @Size(max = 200)
    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "noyau_couvain", nullable = false)
    private boolean noyauCouvain = true;

    @Column(name = "noyau_reine", nullable = false)
    private boolean noyauReine = true;

    @Column(name = "noyau_cadres", nullable = false)
    private boolean noyauCadres = true;

    @Column(name = "noyau_temperament", nullable = false)
    private boolean noyauTemperament = true;

    /**
     * Gabarit propose par defaut a la saisie.
     *
     * <p>Un seul par tenant : un index unique partiel ({@code uq_gabarit_defaut})
     * le fait respecter par la base, et le service bascule l'ancien avant de
     * poser le nouveau.
     */
    @Column(name = "par_defaut", nullable = false)
    private boolean parDefaut;

    @Column(name = "actif", nullable = false)
    private boolean actif = true;

    protected GabaritInspection() {
        // Requis par JPA.
    }

    public GabaritInspection(String nom) {
        this.nom = nom;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isNoyauCouvain() {
        return noyauCouvain;
    }

    public void setNoyauCouvain(boolean noyauCouvain) {
        this.noyauCouvain = noyauCouvain;
    }

    public boolean isNoyauReine() {
        return noyauReine;
    }

    public void setNoyauReine(boolean noyauReine) {
        this.noyauReine = noyauReine;
    }

    public boolean isNoyauCadres() {
        return noyauCadres;
    }

    public void setNoyauCadres(boolean noyauCadres) {
        this.noyauCadres = noyauCadres;
    }

    public boolean isNoyauTemperament() {
        return noyauTemperament;
    }

    public void setNoyauTemperament(boolean noyauTemperament) {
        this.noyauTemperament = noyauTemperament;
    }

    public boolean isParDefaut() {
        return parDefaut;
    }

    public void setParDefaut(boolean parDefaut) {
        this.parDefaut = parDefaut;
    }

    public boolean isActif() {
        return actif;
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }
}
