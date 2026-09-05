package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/**
 * Point d'observation du referentiel ferme (SPRINT-28, lot I).
 *
 * <p>Ferme la ligne « modeles d'inspection reutilisables, champs activables » du
 * §3 de {@code docs/ECART-CONCURRENTS.md}, et sa jumelle « saisie par cases a
 * cocher ».
 *
 * <p><strong>Sans {@code tenantId}, et c'est la decision du lot.</strong> Un
 * formulaire parametrable detruit la statistique s'il autorise des champs
 * libres : dix exploitations inventeraient dix libelles pour la meme
 * observation, et plus rien ne se compterait — exactement le defaut que le
 * SPRINT-20 corrigeait en remplacant du texte libre par des colonnes. Le
 * referentiel est donc le meme pour tout le monde par construction, et un
 * gabarit ACTIVE des cases existantes sans jamais en inventer.
 *
 * <p><strong>{@link Immutable}, et pas seulement par politesse.</strong> La
 * migration V28 retire {@code INSERT}, {@code UPDATE} et {@code DELETE} au role
 * applicatif sur cette table : la fermeture est tenue par PostgreSQL, cette
 * annotation ne fait que l'annoncer plus tot : une ecriture echouerait de toute
 * facon, autant qu'Hibernate ne la tente pas.
 *
 * <p>Le {@code libelle} est francais et n'est <strong>pas</strong> la
 * traduction : les trois langues vivent dans les fichiers de locale du front,
 * indexes par {@code code}. Il sert au serveur — fiche PDF, export CSV, tous
 * deux en francais — et de repli pour un client qui rencontrerait un point
 * ajoute apres sa derniere mise a jour.
 */
@Entity
@Table(name = "point_observation")
@Immutable
public class PointObservation {

    @Id
    @Column(name = "code", nullable = false, length = 40, updatable = false)
    private String code;

    @Column(name = "categorie", nullable = false, length = 20, updatable = false)
    private String categorie;

    @Column(name = "type_valeur", nullable = false, length = 10, updatable = false)
    private String typeValeur;

    @Column(name = "libelle", nullable = false, length = 80, updatable = false)
    private String libelle;

    @Column(name = "ordre", nullable = false, updatable = false)
    private Integer ordre;

    protected PointObservation() {
        // Requis par JPA. Aucun constructeur public : cette table ne s'ecrit
        // que par migration.
    }

    public String getCode() {
        return code;
    }

    public String getCategorie() {
        return categorie;
    }

    public String getTypeValeur() {
        return typeValeur;
    }

    /** Vrai si ce point attend une intensite plutot qu'une case cochee. */
    public boolean estEchelle() {
        return "echelle".equals(typeValeur);
    }

    public String getLibelle() {
        return libelle;
    }

    public Integer getOrdre() {
        return ordre;
    }
}
