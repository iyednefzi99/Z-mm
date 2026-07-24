package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Recolte de miel d'une ruche (US-033), identifiee par un numero de lot unique
 * (dans le tenant) qui sert de cle de tracabilite au QR code cote client.
 */
@Entity
@Table(name = "recolte")
public class Recolte extends EntiteTenant {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruche_id", nullable = false)
    private Ruche ruche;

    @NotNull
    @Column(name = "date_recolte", nullable = false)
    private LocalDate dateRecolte;

    @NotNull
    @PositiveOrZero
    @Column(name = "quantite_kg", nullable = false, precision = 8, scale = 3)
    private BigDecimal quantiteKg;

    @Size(max = 60)
    @Column(name = "type_miel", length = 60)
    private String typeMiel;

    @NotNull
    @Column(name = "lot", nullable = false, length = 40)
    private String lot;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected Recolte() {
        // Requis par JPA.
    }

    public Recolte(Ruche ruche, LocalDate dateRecolte, BigDecimal quantiteKg, String lot) {
        this.ruche = ruche;
        this.dateRecolte = dateRecolte;
        this.quantiteKg = quantiteKg;
        this.lot = lot;
    }

    public Ruche getRuche() {
        return ruche;
    }

    public LocalDate getDateRecolte() {
        return dateRecolte;
    }

    public void setDateRecolte(LocalDate dateRecolte) {
        this.dateRecolte = dateRecolte;
    }

    public BigDecimal getQuantiteKg() {
        return quantiteKg;
    }

    public void setQuantiteKg(BigDecimal quantiteKg) {
        this.quantiteKg = quantiteKg;
    }

    public String getTypeMiel() {
        return typeMiel;
    }

    public void setTypeMiel(String typeMiel) {
        this.typeMiel = typeMiel;
    }

    public String getLot() {
        return lot;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
