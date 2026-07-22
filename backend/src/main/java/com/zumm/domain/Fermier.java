package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Fermier — proprietaire exploitant (US-001), racine de la hierarchie
 * Fermier -> Ferme -> Site du dictionnaire de donnees.
 */
@Entity
@Table(name = "fermier")
public class Fermier extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "nom", nullable = false, length = 120)
    private String nom;

    @Size(max = 180)
    @Column(name = "contact", length = 180)
    private String contact;

    protected Fermier() {
        // Requis par JPA.
    }

    public Fermier(String nom, String contact) {
        this.nom = nom;
        this.contact = contact;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }
}
