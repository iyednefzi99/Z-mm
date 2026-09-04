package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Un consommable en stock, et le seuil qui le rend utile (SPRINT-27, lot E).
 *
 * <p><strong>Un stock sans seuil est un inventaire</strong> : il dit ce qu'on a,
 * jamais ce qui manque. Le seuil est donc obligatoire, avec un défaut à zéro —
 * un seuil nullable désactiverait silencieusement la seule fonction qui compte,
 * celle qui fait apparaître « à racheter » dans la liste.
 */
@Entity
@Table(name = "consommable")
public class Consommable extends EntiteTenant {

    @NotBlank
    @Size(max = 120)
    @Column(name = "libelle", nullable = false, length = 120)
    private String libelle;

    @NotNull
    @Pattern(regexp = "sirop|candi|traitement|cire_gaufree|pot|etiquette"
            + "|cadre|protection|autre")
    @Column(name = "categorie", nullable = false, length = 20)
    private String categorie;

    @NotNull
    @PositiveOrZero
    @Column(name = "quantite", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantite = BigDecimal.ZERO;

    @NotNull
    @Pattern(regexp = "kg|l|unite")
    @Column(name = "unite", nullable = false, length = 10)
    private String unite;

    @NotNull
    @PositiveOrZero
    @Column(name = "seuil_alerte", nullable = false, precision = 10, scale = 2)
    private BigDecimal seuilAlerte = BigDecimal.ZERO;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    protected Consommable() {
        // Requis par JPA.
    }

    public Consommable(String libelle, String categorie, String unite) {
        this.libelle = libelle;
        this.categorie = categorie;
        this.unite = unite;
    }

    /**
     * Faut-il racheter ?
     *
     * <p>Comparaison INCLUSIVE : arriver pile au seuil, c'est déjà être à court.
     * Un seuil de trois kilos de candi veut dire « il m'en faut toujours trois
     * d'avance », pas « prévenez-moi quand il n'y en aura plus ».
     */
    public boolean sousSeuil() {
        return quantite.compareTo(seuilAlerte) <= 0;
    }

    public String getLibelle() {
        return libelle;
    }

    public void setLibelle(String libelle) {
        this.libelle = libelle;
    }

    public String getCategorie() {
        return categorie;
    }

    public void setCategorie(String categorie) {
        this.categorie = categorie;
    }

    public BigDecimal getQuantite() {
        return quantite;
    }

    public void setQuantite(BigDecimal quantite) {
        this.quantite = quantite;
    }

    public String getUnite() {
        return unite;
    }

    public void setUnite(String unite) {
        this.unite = unite;
    }

    public BigDecimal getSeuilAlerte() {
        return seuilAlerte;
    }

    public void setSeuilAlerte(BigDecimal seuilAlerte) {
        this.seuilAlerte = seuilAlerte;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
