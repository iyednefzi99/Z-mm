package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/**
 * Socle commun des entites metier multi-tenant (ADR-001).
 *
 * <p>Le champ {@code tenantId} porte l'annotation Hibernate {@link TenantId} : sa
 * valeur n'est <strong>jamais</strong> renseignee par le code applicatif. Hibernate
 * la pose automatiquement a l'insertion, a partir du tenant courant
 * ({@code TenantIdentifierResolver}), et l'ajoute comme discriminant a chaque
 * lecture, mise a jour et suppression. La politique RLS PostgreSQL fait double
 * garde au niveau du SGBD : les deux couches doivent converger.
 *
 * <p>{@code creeLe} et {@code majLe} sont gerees par la base (valeur par defaut et
 * trigger, cf. migration V2) : elles sont donc en lecture seule cote JPA.
 */
@MappedSuperclass
public abstract class EntiteTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "maj_le", nullable = false, insertable = false, updatable = false)
    private Instant majLe;

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public Instant getMajLe() {
        return majLe;
    }
}
