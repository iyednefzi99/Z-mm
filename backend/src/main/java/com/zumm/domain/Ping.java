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
 * <p><strong>Conservee volontairement</strong>, contrairement a ce que cette
 * javadoc et l'en-tete de la migration V1 annoncaient. La dette « nettoyer ping » a
 * ete reconduite des SPRINT-01 a 07 avant d'etre requalifiee en decision : la sonde
 * a une valeur propre. {@code WalkingSkeletonIT} s'en sert pour prouver, sur une
 * base reelle, que Flyway, PostGIS, TimescaleDB et la persistance JPA tiennent de
 * bout en bout — une verification qu'aucune entite metier ne rend mieux, parce
 * qu'aucune n'est aussi simple.
 *
 * <p><strong>Exception a connaitre.</strong> {@code ping} est la seule table sans
 * {@code tenant_id} ni RLS, alors que la migration V3 accorde le DML a
 * {@code zumm_app} sur toutes les tables. Ce n'est pas une brèche — la table ne
 * porte ni donnee metier ni donnee de tenant, et rien ne s'y rattache — mais c'est
 * une exception a l'invariant « toute table porte sa politique », consignee ici
 * pour ne pas etre redecouverte en audit. La retirer demanderait de reecrire la
 * sonde ; l'en-tete de V1 ne peut pas etre corrige, un commentaire faisant partie
 * du checksum Flyway d'une migration deja appliquee.
 *
 * <p>Toute table METIER, elle, porte {@code tenant_id}, sa politique RLS et une
 * cle etrangere composite — sans exception.
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
