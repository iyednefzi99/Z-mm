package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Saisie de visite en cours, reprenable sur un autre appareil (SPRINT-24).
 *
 * <p>Ferme la ligne « brouillon de visite reprenable » du §13 de
 * {@code docs/ECART-CONCURRENTS.md}. Trois éditeurs conseillent à leurs
 * utilisateurs de noter au rucher puis de saisir au retour : la file de
 * mutations hors ligne ({@code offline/file.ts}) résout le trajet
 * <em>terrain → serveur</em>, jamais le trajet <em>téléphone → ordinateur</em>.
 *
 * <p><strong>Ce n'est pas une visite, et c'est délibéré.</strong> Une visite est
 * un acte, dont le registre, les agrégats, les exports et les règles se servent.
 * Un brouillon est une saisie en cours : incomplète, parfois incohérente, et
 * susceptible de ne jamais devenir une visite. Ajouter un état {@code brouillon}
 * à {@link Visite} aurait obligé chaque lecture du registre à se souvenir de
 * l'exclure — il aurait suffi d'un oubli pour qu'une saisie abandonnée entre
 * dans un comptage réglementaire.
 *
 * <p><strong>Le contenu est opaque au serveur</strong> : du JSON que le front
 * écrit et relit. Le valider reviendrait à exiger d'une saisie en cours qu'elle
 * soit déjà complète, ce qui est exactement le contraire du besoin.
 */
@Entity
@Table(name = "brouillon_visite")
public class BrouillonVisite extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotBlank
    @Size(max = 262144)
    @Column(name = "contenu", nullable = false, columnDefinition = "text")
    private String contenu;

    /**
     * Appareil d'où vient la dernière écriture, tel que le navigateur le nomme.
     *
     * <p>Sans lui, l'agent qui retrouve un brouillon à la maison ne sait pas
     * s'il vient de son téléphone ou d'un poste partagé au local.
     */
    @Size(max = 80)
    @Column(name = "appareil", length = 80)
    private String appareil;

    protected BrouillonVisite() {
        // JPA
    }

    public BrouillonVisite(Agent agent, Ruche ruche, String contenu, String appareil) {
        this.agent = agent;
        this.ruche = ruche;
        this.contenu = contenu;
        this.appareil = appareil;
    }

    public Agent getAgent() {
        return agent;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public String getContenu() {
        return contenu;
    }

    public void setContenu(String contenu) {
        this.contenu = contenu;
    }

    public String getAppareil() {
        return appareil;
    }

    public void setAppareil(String appareil) {
        this.appareil = appareil;
    }
}
