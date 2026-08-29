package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Maladie ou ravageur <strong>nomme</strong>, constate lors d'une visite
 * (SPRINT-20).
 *
 * <p>Jusqu'ici, {@code EtatSante} offrait un curseur a trois positions — bon,
 * moyen, mauvais — et « loque americaine » ne pouvait s'ecrire que dans
 * {@code Visite.constatations}, en texte libre, ou rien ne se compte ni ne se
 * compare. Une entite fille plutot que des colonnes : une visite peut en
 * constater plusieurs.
 *
 * <p>{@code gravite} vaut « suspectee » par defaut, et c'est une position
 * honnete : au rucher on constate un symptome, on ne pose pas un diagnostic de
 * laboratoire. Presenter la suspicion comme une certitude fausserait toute
 * statistique sanitaire construite dessus.
 */
@Entity
@Table(name = "observation_pathologie")
public class ObservationPathologie extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visite_id", nullable = false)
    private Visite visite;

    @NotNull
    @Pattern(regexp = "varroose|loque_americaine|loque_europeenne|nosemose"
            + "|petit_coleoptere|fausse_teigne|frelon_asiatique"
            + "|couvain_sacciforme|mycose|pesticide|autre")
    @Column(name = "pathologie", nullable = false, length = 30)
    private String pathologie;

    @NotNull
    @Pattern(regexp = "suspectee|legere|moderee|severe")
    @Column(name = "gravite", nullable = false, length = 10)
    private String gravite = "suspectee";

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected ObservationPathologie() {
        // Requis par JPA.
    }

    public ObservationPathologie(Visite visite, String pathologie, String gravite) {
        this.visite = visite;
        this.pathologie = pathologie;
        this.gravite = gravite;
    }

    public Visite getVisite() {
        return visite;
    }

    public String getPathologie() {
        return pathologie;
    }

    public void setPathologie(String pathologie) {
        this.pathologie = pathologie;
    }

    public String getGravite() {
        return gravite;
    }

    public void setGravite(String gravite) {
        this.gravite = gravite;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
