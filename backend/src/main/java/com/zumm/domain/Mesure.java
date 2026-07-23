package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.hibernate.annotations.TenantId;

/**
 * Mesure d'un indicateur de ruche a un instant donne (US-016).
 *
 * <p>Stockee en hypertable TimescaleDB (cf. migration V5). Le modele est pose ce
 * sprint ; l'ingestion et les alertes viennent avec l'EPIC-004. Multi-tenant par
 * {@code tenant_id} + RLS, comme les autres entites — ici le discriminant
 * {@code @TenantId} porte sur une entite a cle composite.
 */
@Entity
@Table(name = "mesure")
public class Mesure {

    @EmbeddedId
    private MesureId id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @NotNull
    @Column(name = "valeur", nullable = false, precision = 12, scale = 4)
    private BigDecimal valeur;

    protected Mesure() {
        // Requis par JPA.
    }

    public Mesure(MesureId id, BigDecimal valeur) {
        this.id = id;
        this.valeur = valeur;
    }

    public MesureId getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public BigDecimal getValeur() {
        return valeur;
    }
}
