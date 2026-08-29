package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Apport nourricier a une ruche (SPRINT-20).
 *
 * <p>Deuxieme acte que {@code RaisonVisite.NOURRISSAGE} ne savait que nommer. Il
 * compte pour deux raisons : le sirop pose sous une hausse se retrouve dans la
 * recolte, et le bilan d'une saison ne se lit pas sans ce qu'on a rendu a la
 * colonie.
 *
 * <p>Les deux sirops sont des valeurs distinctes parce qu'ils ne servent pas a
 * la meme chose : le 1:1 stimule la ponte au printemps, le 2:1 constitue les
 * reserves d'hiver. Les confondre rendrait le motif illisible.
 */
@Entity
@Table(name = "nourrissement")
public class Nourrissement extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visite_id")
    private Visite visite;

    @NotNull
    @Column(name = "date_apport", nullable = false)
    private LocalDate dateApport;

    @NotNull
    @Pattern(regexp = "sirop_1_1|sirop_2_1|candi|pollen|substitut_pollen|miel|eau")
    @Column(name = "type_aliment", nullable = false, length = 20)
    private String typeAliment;

    @NotNull
    @Positive
    @Column(name = "quantite", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantite;

    @NotNull
    @Pattern(regexp = "kg|g|l|ml")
    @Column(name = "quantite_unite", nullable = false, length = 10)
    private String quantiteUnite;

    @Pattern(regexp = "stimulation|hivernage|disette|secours|transhumance|autre")
    @Column(name = "motif", length = 20)
    private String motif;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected Nourrissement() {
        // Requis par JPA.
    }

    public Nourrissement(Ruche ruche, Agent agent, LocalDate dateApport,
            String typeAliment, BigDecimal quantite, String quantiteUnite) {
        this.ruche = ruche;
        this.agent = agent;
        this.dateApport = dateApport;
        this.typeAliment = typeAliment;
        this.quantite = quantite;
        this.quantiteUnite = quantiteUnite;
    }

    public Ruche getRuche() {
        return ruche;
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

    public LocalDate getDateApport() {
        return dateApport;
    }

    public void setDateApport(LocalDate dateApport) {
        this.dateApport = dateApport;
    }

    public String getTypeAliment() {
        return typeAliment;
    }

    public void setTypeAliment(String typeAliment) {
        this.typeAliment = typeAliment;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public void setQuantite(BigDecimal quantite) {
        this.quantite = quantite;
    }

    public String getQuantiteUnite() {
        return quantiteUnite;
    }

    public void setQuantiteUnite(String quantiteUnite) {
        this.quantiteUnite = quantiteUnite;
    }

    public String getMotif() {
        return motif;
    }

    public void setMotif(String motif) {
        this.motif = motif;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
