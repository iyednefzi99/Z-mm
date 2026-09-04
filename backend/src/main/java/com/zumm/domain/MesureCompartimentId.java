package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Cle d'une mesure de compartiment : un compartiment, un indicateur, un instant. */
@Embeddable
public class MesureCompartimentId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "compartiment_id", nullable = false)
    private Long compartimentId;

    @Column(name = "type_indicateur", nullable = false, length = 20)
    private TypeIndicateur typeIndicateur;

    @Column(name = "instant", nullable = false)
    private Instant instant;

    protected MesureCompartimentId() {
        // Requis par JPA.
    }

    public MesureCompartimentId(Long compartimentId, TypeIndicateur typeIndicateur,
            Instant instant) {
        this.compartimentId = compartimentId;
        this.typeIndicateur = typeIndicateur;
        this.instant = instant;
    }

    public Long getCompartimentId() {
        return compartimentId;
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
        if (!(autre instanceof MesureCompartimentId id)) {
            return false;
        }
        return Objects.equals(compartimentId, id.compartimentId)
                && typeIndicateur == id.typeIndicateur
                && Objects.equals(instant, id.instant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(compartimentId, typeIndicateur, instant);
    }
}
