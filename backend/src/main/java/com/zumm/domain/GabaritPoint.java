package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.TenantId;

/**
 * Point du referentiel retenu par un gabarit (SPRINT-28, lot I).
 *
 * <p>Une table de liaison, et rien de plus : la cle etrangere vers
 * {@link PointObservation} est ce qui rend le referentiel reellement ferme —
 * un gabarit ne peut pas retenir un point qui n'existe pas.
 */
@Entity
@Table(name = "gabarit_point")
public class GabaritPoint {

    /** Cle d'un point de gabarit : un gabarit, un code de point. */
    @Embeddable
    public static class Cle implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "gabarit_id", nullable = false)
        private Long gabaritId;

        @Column(name = "point_code", nullable = false, length = 40)
        private String pointCode;

        protected Cle() {
            // Requis par JPA.
        }

        public Cle(Long gabaritId, String pointCode) {
            this.gabaritId = gabaritId;
            this.pointCode = pointCode;
        }

        public Long getGabaritId() {
            return gabaritId;
        }

        public String getPointCode() {
            return pointCode;
        }

        @Override
        public boolean equals(Object autre) {
            if (this == autre) {
                return true;
            }
            if (!(autre instanceof Cle cle)) {
                return false;
            }
            return Objects.equals(gabaritId, cle.gabaritId)
                    && Objects.equals(pointCode, cle.pointCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gabaritId, pointCode);
        }
    }

    @EmbeddedId
    private Cle id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "ordre", nullable = false)
    private int ordre;

    protected GabaritPoint() {
        // Requis par JPA.
    }

    public GabaritPoint(Long gabaritId, String pointCode, int ordre) {
        this.id = new Cle(gabaritId, pointCode);
        this.ordre = ordre;
    }

    public Cle getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getOrdre() {
        return ordre;
    }
}
