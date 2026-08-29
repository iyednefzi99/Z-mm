package com.zumm.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Ruche et sa composition (US-004, pattern Composite).
 *
 * <p>Hebergee par un site (emplacement) et appartenant a une ferme (proprietaire,
 * distincte du site), eventuellement sous la responsabilite d'un agent. Sa
 * composition — un corps obligatoire et jusqu'a cinq hausses — est portee par les
 * {@link Compartiment}, geres en cascade. Les regles de cardinalite sont
 * appliquees par le service (contraintes inter-lignes).
 */
@Entity
@Table(name = "ruche")
public class Ruche extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "modele", nullable = false, length = 120)
    private String modele;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ferme_id", nullable = false)
    private Ferme ferme;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_responsable_id")
    private Agent agentResponsable;

    @NotNull
    @Column(name = "etat", nullable = false, length = 20)
    private EtatRuche etat = EtatRuche.CREEE;

    @OneToMany(mappedBy = "ruche", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Compartiment> compartiments = new ArrayList<>();

    // ─── Referentiel et reperage (SPRINT-20) ────────────────────────────────

    /**
     * Type au referentiel : Langstroth, Dadant, Warre, Voirnot, Top-Bar, Kenyane.
     *
     * <p>{@link #modele} reste le texte libre — « Dadant 10 cadres, fond
     * grillage Nicot ». Le type est le referentiel AU-DESSUS, et c'est lui qui
     * rend possible une statistique par type : un texte libre l'interdisait,
     * puisque « Dadant 10 » et « dadant 10c » n'y sont pas la meme chose.
     */
    @Pattern(regexp = "langstroth|dadant|warre|voirnot|top_bar|kenyane|autre")
    @Column(name = "type_ruche", length = 15)
    private String typeRuche;

    /** Couleur de la ruche : le reperage visuel au rucher, avant tout scan. */
    @Pattern(regexp = "blanc|jaune|orange|rouge|vert|bleu|violet|gris|bois")
    @Column(name = "couleur", length = 15)
    private String couleur;

    /** D'ou vient la colonie : essaim capture, division, nucleus, achat. */
    @Pattern(regexp = "essaim_capture|essaim_achete|division|nucleus|paquet|achat|autre")
    @Column(name = "origine", length = 20)
    private String origine;

    /**
     * Pourquoi la ruche est cloturee.
     *
     * <p>Distingue ce que {@link EtatRuche#CLOTUREE} confondait : une ruche
     * morte, une ruche vendue et une ruche fusionnee etaient indiscernables en
     * statistique, alors que la premiere est une perte et la deuxieme une
     * recette. La base refuse cette valeur sur une ruche encore active.
     */
    @Pattern(regexp = "morte|fusionnee|vendue|volee|reformee|essaimee|autre")
    @Column(name = "cause_cloture", length = 15)
    private String causeCloture;

    protected Ruche() {
        // Requis par JPA.
    }

    public Ruche(String modele, Site site, Ferme ferme, EtatRuche etat) {
        this.modele = modele;
        this.site = site;
        this.ferme = ferme;
        this.etat = etat;
    }

    /** Ajoute un compartiment en maintenant le lien bidirectionnel. */
    public void ajouterCompartiment(Compartiment compartiment) {
        compartiment.setRuche(this);
        compartiments.add(compartiment);
    }

    /** Vide la composition (avant de la reconstruire lors d'une mise a jour). */
    public void viderCompartiments() {
        compartiments.clear();
    }

    public String getModele() {
        return modele;
    }

    public void setModele(String modele) {
        this.modele = modele;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Ferme getFerme() {
        return ferme;
    }

    public void setFerme(Ferme ferme) {
        this.ferme = ferme;
    }

    public Agent getAgentResponsable() {
        return agentResponsable;
    }

    public void setAgentResponsable(Agent agentResponsable) {
        this.agentResponsable = agentResponsable;
    }

    public EtatRuche getEtat() {
        return etat;
    }

    public void setEtat(EtatRuche etat) {
        this.etat = etat;
    }

    public List<Compartiment> getCompartiments() {
        return compartiments;
    }

    public String getTypeRuche() {
        return typeRuche;
    }

    public void setTypeRuche(String typeRuche) {
        this.typeRuche = typeRuche;
    }

    public String getCouleur() {
        return couleur;
    }

    public void setCouleur(String couleur) {
        this.couleur = couleur;
    }

    public String getOrigine() {
        return origine;
    }

    public void setOrigine(String origine) {
        this.origine = origine;
    }

    public String getCauseCloture() {
        return causeCloture;
    }

    public void setCauseCloture(String causeCloture) {
        this.causeCloture = causeCloture;
    }
}
