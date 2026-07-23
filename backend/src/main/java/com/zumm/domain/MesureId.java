package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Cle naturelle d'une {@link Mesure} : ruche, indicateur, instant. La colonne de
 * partitionnement {@code instant} DOIT figurer dans la cle (exigence hypertable
 * TimescaleDB), d'ou l'absence d'identifiant de substitution.
 */
@Embeddable
public class MesureId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "ruche_id", nullable = false)
    private Long rucheId;

    @Column(name = "type_indicateur", nullable = false, length = 20)
    private TypeIndicateur typeIndicateur;

    @Column(name = "instant", nullable = false)
    private Instant instant;

    protected MesureId() {
        // Requis par JPA.
    }

    public MesureId(Long rucheId, TypeIndicateur typeIndicateur, Instant instant) {
        this.rucheId = rucheId;
        this.typeIndicateur = typeIndicateur;
        this.instant = instant;
    }

    public Long getRucheId() {
        return rucheId;
    }

    public TypeIndicateur getTypeIndicateur() {
        return typeIndicateur;
    }

    public Instant getInstant() {
        return instant;
    }

    @Override
    public boolean equals(Object autre) {
        if (this == autre) {
            return true;
        }
        if (!(autre instanceof MesureId id)) {
            return false;
        }
        return Objects.equals(rucheId, id.rucheId)
                && typeIndicateur == id.typeIndicateur
                && Objects.equals(instant, id.instant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rucheId, typeIndicateur, instant);
    }
}
