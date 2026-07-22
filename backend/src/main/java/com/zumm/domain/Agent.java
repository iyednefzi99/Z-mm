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
 * Agent — intervenant dote d'un role metier (US-005).
 *
 * <p>Le rattachement a une ferme est optionnel : un role transverse (par exemple
 * {@link RoleAgent#ADMIN}) n'est pas lie a une exploitation particuliere. Le
 * {@code tenant_id} reste, lui, obligatoire (gere par {@link EntiteTenant}).
 */
@Entity
@Table(name = "agent")
public class Agent extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "nom", nullable = false, length = 120)
    private String nom;

    @NotNull
    @Column(name = "role", nullable = false, length = 20)
    private RoleAgent role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ferme_id")
    private Ferme ferme;

    protected Agent() {
        // Requis par JPA.
    }

    public Agent(String nom, RoleAgent role, Ferme ferme) {
        this.nom = nom;
        this.role = role;
        this.ferme = ferme;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public RoleAgent getRole() {
        return role;
    }

    public void setRole(RoleAgent role) {
        this.role = role;
    }

    public Ferme getFerme() {
        return ferme;
    }

    public void setFerme(Ferme ferme) {
        this.ferme = ferme;
    }
}
