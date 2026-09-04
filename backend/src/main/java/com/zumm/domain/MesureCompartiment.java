package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.hibernate.annotations.TenantId;

/**
 * Poids attribue a un COMPARTIMENT — un corps, une hausse (SPRINT-26, lot F1).
 *
 * <p>Ferme la ligne « poids par hausse » du §5 : la granularite des mesures
 * s'arretait a la ruche, alors que {@link Compartiment} distingue depuis le
 * SPRINT-02 le corps des hausses.
 *
 * <p><strong>Entite distincte de {@link Mesure}, et ce n'est pas un doublon.</strong>
 * {@code Mesure} porte ce qu'une balance pese SOUS la ruche entiere ; celle-ci
 * porte ce qu'on attribue a une hausse. Les melanger dans une seule table aurait
 * demande de rendre nullable une colonne de la cle primaire d'une hypertable —
 * la table la plus critique du systeme, celle que lisent les alertes, la
 * prevision de recolte et la detection d'anomalie — ou d'inventer un sentinel
 * qui aurait fait perdre la cle etrangere. Et surtout : chaque lecture existante
 * aurait du se souvenir d'exclure les lignes de hausse, faute de quoi une
 * prevision de recolte compterait deux fois le meme miel.
 *
 * <p>Le poids SEUL, pour l'instant : la temperature et l'humidite par hausse
 * n'existent chez aucun concurrent, et les autoriser serait promettre une
 * granularite qu'aucun materiel ne produit.
 */
@Entity
@Table(name = "mesure_compartiment")
public class MesureCompartiment {

    @EmbeddedId
    private MesureCompartimentId id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @NotNull
    @Column(name = "valeur", nullable = false, precision = 12, scale = 4)
    private BigDecimal valeur;

    protected MesureCompartiment() {
        // Requis par JPA.
    }

    public MesureCompartiment(MesureCompartimentId id, BigDecimal valeur) {
        this.id = id;
        this.valeur = valeur;
    }

    public MesureCompartimentId getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public BigDecimal getValeur() {
        return valeur;
    }
}
