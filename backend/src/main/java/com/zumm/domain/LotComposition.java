package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/**
 * Part d'une origine dans un lot de conditionnement (US-056).
 *
 * <p>La recolte est FACULTATIVE, et c'est le point de modelisation qui compte :
 * un conditionneur melange couramment sa propre production avec du miel acquis a
 * un tiers. Si l'on exigeait une recolte, ce miel-la serait inrepresentable — les
 * pourcentages ne totaliseraient jamais 100 % et la mention d'origine imprimee
 * serait fausse. Ici, du miel achete se declare par son seul pays d'origine.
 *
 * <p>Entite autonome plutot que derivee de {@link EntiteTenant} : une part de
 * composition ne se modifie pas (on refait la composition), elle n'a donc pas de
 * {@code maj_le}.
 */
@Entity
@Table(name = "lot_composition")
public class LotComposition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private LotConditionnement lot;

    /** Recolte d'origine, ou {@code null} pour du miel acquis a un tiers. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recolte_id")
    private Recolte recolte;

    @NotNull
    @Pattern(regexp = "^[A-Z]{2}$", message = "Code pays ISO 3166-1 alpha-2 attendu (ex. FR).")
    @Column(name = "pays_origine", nullable = false, length = 2)
    private String paysOrigine;

    @NotNull
    @Column(name = "pourcentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal pourcentage;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    protected LotComposition() {
        // Requis par JPA.
    }

    public LotComposition(Recolte recolte, String paysOrigine, BigDecimal pourcentage) {
        this.recolte = recolte;
        this.paysOrigine = paysOrigine;
        this.pourcentage = pourcentage;
    }

    void rattacherA(LotConditionnement lot) {
        this.lot = lot;
    }

    public Long getId() {
        return id;
    }

    public LotConditionnement getLot() {
        return lot;
    }

    public Recolte getRecolte() {
        return recolte;
    }

    public String getPaysOrigine() {
        return paysOrigine;
    }

    public BigDecimal getPourcentage() {
        return pourcentage;
    }
}
