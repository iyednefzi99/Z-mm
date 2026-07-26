package com.zumm.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lot de miel mis en pot (US-056), porteur de la mention d'origine exigee par la
 * directive (UE) 2024/1438.
 *
 * <p>A ne pas confondre avec {@link Recolte}, qui est la maille de PRODUCTION —
 * une ruche, une date. Ce qui part en pot est presque toujours un MELANGE de
 * recoltes, parfois complete par du miel acquis a un tiers. C'est ce melange que
 * l'etiquette doit decrire, et c'est donc lui, et non la recolte, qui porte les
 * pourcentages d'origine.
 *
 * <p>La composition est un agregat au sens propre : elle n'existe pas hors de son
 * lot, ne se manipule que par lui, et disparait avec lui ({@code orphanRemoval}).
 * Le calcul de la mention d'origine est porte par le lot lui-meme plutot que par
 * un service : c'est une regle intrinseque a l'objet, pas une orchestration.
 */
@Entity
@Table(name = "lot_conditionnement")
public class LotConditionnement extends EntiteTenant {

    @NotNull
    @Size(max = 40)
    @Column(name = "reference", nullable = false, length = 40)
    private String reference;

    @NotNull
    @Column(name = "date_conditionnement", nullable = false)
    private LocalDate dateConditionnement;

    @NotNull
    @Positive
    @Column(name = "quantite_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantiteKg;

    @Size(max = 60)
    @Column(name = "type_miel", length = 60)
    private String typeMiel;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @OneToMany(mappedBy = "lot", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("pourcentage DESC")
    private List<LotComposition> composition = new ArrayList<>();

    protected LotConditionnement() {
        // Requis par JPA.
    }

    public LotConditionnement(String reference, LocalDate dateConditionnement,
            BigDecimal quantiteKg) {
        this.reference = reference;
        this.dateConditionnement = dateConditionnement;
        this.quantiteKg = quantiteKg;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public LocalDate getDateConditionnement() {
        return dateConditionnement;
    }

    public void setDateConditionnement(LocalDate dateConditionnement) {
        this.dateConditionnement = dateConditionnement;
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

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<LotComposition> getComposition() {
        return composition;
    }

    /** Vide la composition : utilise avant de la reconstituer entierement. */
    public void viderComposition() {
        composition.clear();
    }

    /** Ajoute une part et maintient les deux cotes de l'association. */
    public void ajouter(LotComposition part) {
        part.rattacherA(this);
        composition.add(part);
    }
}
