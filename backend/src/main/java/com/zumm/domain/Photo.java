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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/**
 * Photo d'inspection attachee a une visite (US-010, US-028). On stocke la
 * reference ({@code url}) et une legende ; le binaire est hors base. Entite sans
 * {@code maj_le} (une photo ne se modifie pas), d'ou un mapping autonome plutot
 * que via {@link EntiteTenant}.
 */
@Entity
@Table(name = "photo")
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visite_id", nullable = false)
    private Visite visite;

    @NotBlank
    @Size(max = 500)
    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @Size(max = 200)
    @Column(name = "legende", length = 200)
    private String legende;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    protected Photo() {
        // Requis par JPA.
    }

    public Photo(Visite visite, String url, String legende) {
        this.visite = visite;
        this.url = url;
        this.legende = legende;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Visite getVisite() {
        return visite;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLegende() {
        return legende;
    }

    public void setLegende(String legende) {
        this.legende = legende;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
