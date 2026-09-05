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
import java.time.LocalDate;

/**
 * Floraison OBSERVEE d'une ressource, une annee donnee (SPRINT-32, lot H).
 *
 * <p>Ferme la ligne « calendrier de floraison / suivi des miellees » du §2, dont
 * le reproche etait net : « Zumm n'a aucune notion de saison mellifere ».
 *
 * <p><strong>A ne pas confondre avec la floraison DECLAREE</strong> de la `V21`
 * ({@code RessourceFlorale.moisDebut} / {@code moisFin}). Celle-la est une
 * connaissance generale — « le colza fleurit en avril » — utile a la
 * planification, et fausse trois annees sur dix : une gelee tardive decale tout
 * d'une quinzaine.
 *
 * <p>Celle-ci porte ce qui a ete VU, cette annee-la, sur ce rucher-la. Les deux
 * coexistent et ne se remplacent pas : le declaratif prevoit, l'observe
 * constate. Les fondre ferait perdre la seule chose qui permette de dire
 * « cette annee, c'etait en avance de dix jours ».
 */
@Entity
@Table(name = "floraison_observee")
public class FloraisonObservee extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ressource_id", nullable = false)
    private RessourceFlorale ressource;

    @NotNull
    @Min(1990)
    @Max(2100)
    @Column(name = "annee", nullable = false)
    private Integer annee;

    @NotNull
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    /**
     * Pic et fin arrivent APRES le debut, parfois des semaines apres.
     *
     * <p>Les exiger a la saisie obligerait a attendre la fin de la miellee pour
     * noter qu'elle a commence — c'est-a-dire a ne rien noter.
     */
    @Column(name = "date_pic")
    private LocalDate datePic;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    /**
     * Abondance percue, de 0 (nulle) a 3 (exceptionnelle).
     *
     * <p>Une echelle, pas une mesure : personne ne pese le nectar d'une
     * parcelle. Le dire evite qu'on en fasse une moyenne presentee comme un
     * rendement.
     */
    @Min(0)
    @Max(3)
    @Column(name = "abondance")
    private Short abondance;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected FloraisonObservee() {
        // Requis par JPA.
    }

    public FloraisonObservee(RessourceFlorale ressource, Integer annee, LocalDate dateDebut) {
        this.ressource = ressource;
        this.annee = annee;
        this.dateDebut = dateDebut;
    }

    public RessourceFlorale getRessource() {
        return ressource;
    }

    public Integer getAnnee() {
        return annee;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDatePic() {
        return datePic;
    }

    public void setDatePic(LocalDate datePic) {
        this.datePic = datePic;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }

    public Short getAbondance() {
        return abondance;
    }

    public void setAbondance(Short abondance) {
        this.abondance = abondance;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
