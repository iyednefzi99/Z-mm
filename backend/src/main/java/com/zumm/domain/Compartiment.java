package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Compartiment d'une ruche (US-004) : le corps ou une hausse, contenant de 1 a 10
 * cadres. Rattache a sa ruche par une cle etrangere composite incluant tenant_id.
 */
@Entity
@Table(name = "compartiment")
public class Compartiment extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @Column(name = "type", nullable = false, length = 10)
    private TypeCompartiment type;

    @Min(1)
    @Max(10)
    @Column(name = "nb_cadres", nullable = false)
    private int nbCadres;

    protected Compartiment() {
        // Requis par JPA.
    }

    public Compartiment(TypeCompartiment type, int nbCadres) {
        this.type = type;
        this.nbCadres = nbCadres;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public void setRuche(Ruche ruche) {
        this.ruche = ruche;
    }

    public TypeCompartiment getType() {
        return type;
    }

    public void setType(TypeCompartiment type) {
        this.type = type;
    }

    public int getNbCadres() {
        return nbCadres;
    }

    public void setNbCadres(int nbCadres) {
        this.nbCadres = nbCadres;
    }
}
