package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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

    /**
     * Ce qui a ete recolte (SPRINT-27, lot E).
     *
     * <p>« Le modele PRESUPPOSE du miel » disait le §6, et c'etait vrai :
     * `quantiteKg` + `typeMiel` ne laissaient de place ni a la cire, ni au
     * pollen, ni aux essaims. Une colonne suffit a le corriger — une recolte de
     * cire reste une recolte, faite le meme jour, sur la meme ruche, par le meme
     * agent. Lui donner sa propre table aurait duplique la tracabilite, le lot,
     * le forcage de carence et l'export.
     */
    @NotNull
    @Pattern(regexp = "miel|cire|pollen|propolis|gelee_royale|essaim|reine")
    @Column(name = "type_produit", nullable = false, length = 20)
    private String typeProduit = "miel";

    /**
     * kg | unite.
     *
     * <p>Indispensable, et pas cosmetique : cinq essaims ne pesent pas cinq
     * kilogrammes, et deux reines encore moins. Sans elle, `quantiteKg`
     * mentirait des la premiere capture, et le total de production additionnerait
     * des kilos a des individus. La base refuse d'ailleurs les combinaisons
     * incoherentes ({@code ck_recolte_unite_produit}).
     */
    @NotNull
    @Pattern(regexp = "kg|unite")
    @Column(name = "unite", nullable = false, length = 10)
    private String unite = "kg";

    @NotNull
    @Column(name = "lot", nullable = false, length = 40)
    private String lot;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    // ─── Forcage de carence (SPRINT-22) ─────────────────────────────────────

    /**
     * Recolte enregistree malgre une carence en cours.
     *
     * <p>Le refus seul aurait deplace le contournement au lieu de le supprimer :
     * un apiculteur qui ne PEUT pas enregistrer sa recolte cesse d'enregistrer le
     * traitement, et le registre devient faux la ou il n'etait qu'incomplet. On
     * peut donc passer outre — mais on dit pourquoi, et l'acte est audite.
     */
    @Column(name = "carence_forcee", nullable = false)
    private boolean carenceForcee;

    /** Obligatoire des lors qu'on force : une case cochee sans motif ne vaut rien. */
    @Column(name = "motif_forcage", columnDefinition = "text")
    private String motifForcage;

    protected Recolte() {
        // Requis par JPA.
    }

    public Recolte(Ruche ruche, LocalDate dateRecolte, BigDecimal quantiteKg, String lot) {
        this.ruche = ruche;
        this.dateRecolte = dateRecolte;
        this.quantiteKg = quantiteKg;
        this.lot = lot;
    }

    public String getTypeProduit() {
        return typeProduit;
    }

    public void setTypeProduit(String typeProduit) {
        this.typeProduit = typeProduit;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    /** Le miel est le seul produit qui entre dans les totaux de miellee. */
    public boolean estDuMiel() {
        return "miel".equals(typeProduit);
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

    /** Consigne le passage outre, avec son motif. */
    public void forcerCarence(String motif) {
        this.carenceForcee = true;
        this.motifForcage = motif;
    }

    public boolean isCarenceForcee() {
        return carenceForcee;
    }

    public String getMotifForcage() {
        return motifForcage;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
