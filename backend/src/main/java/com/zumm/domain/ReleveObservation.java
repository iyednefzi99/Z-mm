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
 * Valeur relevee pour un point d'observation, sur une visite (SPRINT-28).
 *
 * <p><strong>L'absence de ligne est une information.</strong> Pas de ligne : le
 * point n'a pas ete regarde — il ne figurait pas au gabarit, ou l'inspection
 * s'est arretee avant. Une ligne a {@code false} : regarde, absent. Confondre
 * les deux ferait compter comme « rien constate » des visites ou personne n'a
 * ouvert la ruche, et la statistique serait fausse dans le sens rassurant, qui
 * est le pire des deux.
 *
 * <p>Exactement une des deux valeurs est renseignee, celle qui correspond au
 * type du point. La base l'exige ({@code ck_releve_une_valeur}) sans pouvoir
 * verifier la CORRESPONDANCE — le type vit dans une autre table, hors de portee
 * d'un {@code CHECK}. C'est {@code CarnetService} qui refuse une echelle sur un
 * point booleen.
 */
@Entity
@Table(name = "releve_observation")
public class ReleveObservation {

    /** Cle d'un releve : une visite, un code de point. */
    @Embeddable
    public static class Cle implements Serializable {

        private static final long serialVersionUID = 1L;

        @Column(name = "visite_id", nullable = false)
        private Long visiteId;

        @Column(name = "point_code", nullable = false, length = 40)
        private String pointCode;

        protected Cle() {
            // Requis par JPA.
        }

        public Cle(Long visiteId, String pointCode) {
            this.visiteId = visiteId;
            this.pointCode = pointCode;
        }

        public Long getVisiteId() {
            return visiteId;
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
            return Objects.equals(visiteId, cle.visiteId)
                    && Objects.equals(pointCode, cle.pointCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(visiteId, pointCode);
        }
    }

    @EmbeddedId
    private Cle id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "valeur_bool")
    private Boolean valeurBool;

    @Column(name = "valeur_echelle")
    private Short valeurEchelle;

    protected ReleveObservation() {
        // Requis par JPA.
    }

    public ReleveObservation(Long visiteId, String pointCode, Boolean valeurBool,
            Short valeurEchelle) {
        this.id = new Cle(visiteId, pointCode);
        this.valeurBool = valeurBool;
        this.valeurEchelle = valeurEchelle;
    }

    public Cle getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Boolean getValeurBool() {
        return valeurBool;
    }

    public Short getValeurEchelle() {
        return valeurEchelle;
    }
}
