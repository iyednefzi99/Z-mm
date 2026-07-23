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
}
