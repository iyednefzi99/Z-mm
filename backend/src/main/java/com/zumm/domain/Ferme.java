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
 * Ferme — exploitation rattachee a un fermier (US-002).
 *
 * <p>Le lien vers le fermier est porte par la colonne {@code fermier_id} ; la
 * cle etrangere en base est composite {@code (fermier_id, tenant_id)} et garantit
 * que le fermier reference appartient au meme tenant (cf. migration V2). Le
 * {@code tenant_id} lui-meme est gere par {@link EntiteTenant}.
 */
@Entity
@Table(name = "ferme")
public class Ferme extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "nom", nullable = false, length = 120)
    private String nom;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fermier_id", nullable = false)
    private Fermier fermier;

    protected Ferme() {
        // Requis par JPA.
    }

    public Ferme(String nom, Fermier fermier) {
        this.nom = nom;
        this.fermier = fermier;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public Fermier getFermier() {
        return fermier;
    }

    public void setFermier(Fermier fermier) {
        this.fermier = fermier;
    }
}
