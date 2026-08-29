package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * Comptage de varroa sur une ruche (SPRINT-20).
 *
 * <p>Le parasite que huit des douze catalogues concurrents nomment, et que le
 * depot ne savait ecrire que dans la prose d'une {@code constatation}.
 *
 * <p><strong>L'entite ne porte pas de taux, et c'est le point central.</strong>
 * Le resultat d'un comptage n'a pas la meme unite selon la methode : un lange
 * donne des varroas <em>par jour</em> (chute naturelle), un lavage au sucre ou a
 * l'alcool des varroas <em>pour cent abeilles</em>. Un champ unique melangeant
 * les deux serait faux, et les seuils qu'on en tirerait le seraient aussi. La
 * base garde donc les comptages bruts et la methode ; le taux et son verdict
 * sont calcules par {@code ComptageVarroaService}, la ou l'on peut les
 * expliquer.
 *
 * <p>Le denominateur attendu depend de la methode, et la base l'exige
 * (contrainte {@code ck_varroa_denominateur}) : {@link #estValide()} remonte la
 * meme regle avant l'aller-retour SQL.
 */
@Entity
@Table(name = "comptage_varroa")
public class ComptageVarroa extends EntiteTenant {

    /** Methode dont le denominateur est une duree de pose, et non un echantillon. */
    public static final String METHODE_LANGE = "lange";

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
    @Column(name = "date_comptage", nullable = false)
    private LocalDate dateComptage;

    @NotNull
    @Pattern(regexp = "lange|sucre_glace|alcool|co2|desoperculation")
    @Column(name = "methode", nullable = false, length = 20)
    private String methode;

    @NotNull
    @PositiveOrZero
    @Column(name = "varroas_comptes", nullable = false)
    private Integer varroasComptes;

    /** Denominateur des methodes par echantillon (sucre glace, alcool, CO2). */
    @Column(name = "abeilles_echantillon")
    private Integer abeillesEchantillon;

    /** Denominateur de la chute naturelle : jours de pose du lange. */
    @Column(name = "jours_exposition")
    private Integer joursExposition;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected ComptageVarroa() {
        // Requis par JPA.
    }

    public ComptageVarroa(Ruche ruche, Agent agent, LocalDate dateComptage,
            String methode, Integer varroasComptes) {
        this.ruche = ruche;
        this.agent = agent;
        this.dateComptage = dateComptage;
        this.methode = methode;
        this.varroasComptes = varroasComptes;
    }

    /** Un comptage par lange se compte en jours ; tous les autres en abeilles. */
    public boolean parLange() {
        return METHODE_LANGE.equals(methode);
    }

    /**
     * Le denominateur presente est-il celui qu'exige la methode ?
     *
     * <p>Sans lui la ligne est incalculable, donc inutile — et un comptage qu'on
     * ne peut pas rapporter a une echelle ne dit rien : « 40 varroas » est
     * anodin sur trois jours de lange, alarmant sur 300 abeilles.
     */
    public boolean estValide() {
        return parLange()
                ? joursExposition != null && abeillesEchantillon == null
                : abeillesEchantillon != null && joursExposition == null;
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

    public LocalDate getDateComptage() {
        return dateComptage;
    }

    public void setDateComptage(LocalDate dateComptage) {
        this.dateComptage = dateComptage;
    }

    public String getMethode() {
        return methode;
    }

    public void setMethode(String methode) {
        this.methode = methode;
    }

    public Integer getVarroasComptes() {
        return varroasComptes;
    }

    public void setVarroasComptes(Integer varroasComptes) {
        this.varroasComptes = varroasComptes;
    }

    public Integer getAbeillesEchantillon() {
        return abeillesEchantillon;
    }

    public void setAbeillesEchantillon(Integer abeillesEchantillon) {
        this.abeillesEchantillon = abeillesEchantillon;
    }

    public Integer getJoursExposition() {
        return joursExposition;
    }

    public void setJoursExposition(Integer joursExposition) {
        this.joursExposition = joursExposition;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
