package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alerte de seuil levee par l'ingestion d'une mesure (US-018).
 *
 * <p>Une alerte reste {@code ouverte} tant que l'indicateur n'est pas revenu dans
 * la bande d'hysteresis ; elle est alors fermee ({@code fermeeLe}). L'unicite d'une
 * alerte ouverte par (ruche, indicateur) est garantie en base (index partiel).
 */
@Entity
@Table(name = "alerte")
public class Alerte extends EntiteTenant {

    /** Niveaux possibles, alignes sur la contrainte CHECK de la migration V8. */
    public static final String ATTENTION = "attention";
    public static final String CRITIQUE = "critique";

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @Column(name = "type_indicateur", nullable = false, length = 20)
    private TypeIndicateur typeIndicateur;

    @NotNull
    @Column(name = "niveau", nullable = false, length = 20)
    private String niveau;

    @NotNull
    @Column(name = "message", nullable = false, length = 300)
    private String message;

    @NotNull
    @Column(name = "valeur_declenchement", nullable = false, precision = 12, scale = 4)
    private BigDecimal valeurDeclenchement;

    @Column(name = "ouverte", nullable = false)
    private boolean ouverte = true;

    @Column(name = "ouverte_le", nullable = false, insertable = false, updatable = false)
    private Instant ouverteLe;

    @Column(name = "fermee_le")
    private Instant fermeeLe;

    protected Alerte() {
        // Requis par JPA.
    }

    public Alerte(Ruche ruche, TypeIndicateur typeIndicateur, String niveau, String message,
            BigDecimal valeurDeclenchement) {
        this.ruche = ruche;
        this.typeIndicateur = typeIndicateur;
        this.niveau = niveau;
        this.message = message;
        this.valeurDeclenchement = valeurDeclenchement;
    }

    /** Ferme l'alerte : l'indicateur est revenu dans la bande normale (US-018). */
    public void fermer() {
        this.ouverte = false;
        this.fermeeLe = Instant.now();
    }

    public Ruche getRuche() {
        return ruche;
    }

    public TypeIndicateur getTypeIndicateur() {
        return typeIndicateur;
    }

    public String getNiveau() {
        return niveau;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getValeurDeclenchement() {
        return valeurDeclenchement;
    }

    public boolean isOuverte() {
        return ouverte;
    }

    public Instant getOuverteLe() {
        return ouverteLe;
    }

    public Instant getFermeeLe() {
        return fermeeLe;
    }
}
