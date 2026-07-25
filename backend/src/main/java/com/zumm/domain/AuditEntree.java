package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/**
 * Entrée du journal d'audit (US-043, SPRINT-09).
 *
 * <p>Journal en <b>ajout seul</b> : une entrée est écrite à chaque création,
 * modification ou suppression d'une entité métier, jamais modifiée ensuite. Le
 * {@code tenantId} est posé par Hibernate ({@link TenantId}) comme pour les entités
 * métier, et {@code instant} par la base (défaut {@code now()}).
 */
@Entity
@Table(name = "audit_entree")
public class AuditEntree {

    /** Actions tracées, alignées sur la contrainte CHECK de la migration V12. */
    public static final String CREATION = "creation";
    public static final String MODIFICATION = "modification";
    public static final String SUPPRESSION = "suppression";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "instant", nullable = false, insertable = false, updatable = false)
    private Instant instant;

    @Column(name = "acteur", nullable = false, length = 180)
    private String acteur;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "entite", nullable = false, length = 60)
    private String entite;

    @Column(name = "entite_id")
    private Long entiteId;

    @Column(name = "resume", length = 300)
    private String resume;

    protected AuditEntree() {
        // Requis par JPA.
    }

    public AuditEntree(String acteur, String action, String entite, Long entiteId, String resume) {
        this.acteur = acteur;
        this.action = action;
        this.entite = entite;
        this.entiteId = entiteId;
        this.resume = resume;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Instant getInstant() {
        return instant;
    }

    public String getActeur() {
        return acteur;
    }

    public String getAction() {
        return action;
    }

    public String getEntite() {
        return entite;
    }

    public Long getEntiteId() {
        return entiteId;
    }

    public String getResume() {
        return resume;
    }
}
