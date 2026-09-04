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
import java.time.LocalDate;

/**
 * Division d'une colonie (SPRINT-21, §1 de {@code docs/ECART-CONCURRENTS.md}).
 *
 * <p>{@code RaisonVisite.DIVISION} et {@code EtatRuche.EN_DIVISION} disaient
 * qu'une division avait eu lieu. Ni la ruche fille, ni ce qui lui avait ete
 * transfere n'etaient saisis — donc aucune FILIATION : {@code Ruche.origine}
 * (V19) dit d'ou vient une colonie, jamais DE QUI.
 *
 * <p>La fille est facultative, et ce n'est pas une facilite : on divise souvent
 * vers un nucleus qui ne sera enregistre comme ruche que s'il prend. L'exiger a
 * la saisie ferait renoncer a saisir la division, c'est-a-dire perdre la
 * filiation pour avoir voulu la rendre obligatoire.
 */
@Entity
@Table(name = "division")
public class Division extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_mere_id", nullable = false)
    private Ruche mere;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_fille_id")
    private Ruche fille;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visite_id")
    private Visite visite;

    @NotNull
    @Column(name = "date_division", nullable = false)
    private LocalDate dateDivision;

    @Pattern(regexp = "essaim_artificiel|nucleus|partage_egal|prelevement_cadres|autre")
    @Column(name = "methode", length = 20)
    private String methode;

    @Min(0)
    @Max(40)
    @Column(name = "cadres_couvain")
    private Integer cadresCouvain;

    @Min(0)
    @Max(40)
    @Column(name = "cadres_provisions")
    private Integer cadresProvisions;

    /**
     * Ce que la fille a recu comme reine — la question qui decide de la suite :
     * une division sur cellule royale ne se juge que trois semaines plus tard.
     */
    @Pattern(regexp = "cellule_royale|reine_introduite|orpheline|reine_mere|autre")
    @Column(name = "origine_reine", length = 20)
    private String origineReine;

    @Size(max = 2000)
    @Column(name = "note")
    private String note;

    protected Division() {
        // Requis par JPA.
    }

    public Division(Ruche mere, Agent agent, LocalDate dateDivision) {
        this.mere = mere;
        this.agent = agent;
        this.dateDivision = dateDivision;
    }

    public Ruche getMere() {
        return mere;
    }

    public Ruche getFille() {
        return fille;
    }

    public void setFille(Ruche fille) {
        this.fille = fille;
    }

    public Agent getAgent() {
        return agent;
    }

    public Visite getVisite() {
        return visite;
    }

    public void setVisite(Visite visite) {
        this.visite = visite;
    }

    public LocalDate getDateDivision() {
        return dateDivision;
    }

    public String getMethode() {
        return methode;
    }

    public void setMethode(String methode) {
        this.methode = methode;
    }

    public Integer getCadresCouvain() {
        return cadresCouvain;
    }

    public void setCadresCouvain(Integer cadresCouvain) {
        this.cadresCouvain = cadresCouvain;
    }

    public Integer getCadresProvisions() {
        return cadresProvisions;
    }

    public void setCadresProvisions(Integer cadresProvisions) {
        this.cadresProvisions = cadresProvisions;
    }

    public String getOrigineReine() {
        return origineReine;
    }

    public void setOrigineReine(String origineReine) {
        this.origineReine = origineReine;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
