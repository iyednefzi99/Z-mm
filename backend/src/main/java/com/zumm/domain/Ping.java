package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Entite factice du walking skeleton (SPRINT-00).
 *
 * <p>Elle ne porte aucune semantique metier : son seul role est de prouver que la
 * chaine JPA + Flyway + PostgreSQL fonctionne de bout en bout. Le modele metier
 * reel derive du dictionnaire de donnees et du MLD, et arrive au SPRINT-01.
 *
 * <p><strong>A supprimer</strong> des que les entites metier existent.
 */
@Entity
@Table(name = "ping")
public class Ping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 120)
    @Column(name = "libelle", nullable = false, length = 120)
    private String libelle;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    protected Ping() {
        // Requis par JPA.
    }

    public Ping(String libelle) {
        this.libelle = libelle;
    }

    public Long getId() {
        return id;
    }

    public String getLibelle() {
        return libelle;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
