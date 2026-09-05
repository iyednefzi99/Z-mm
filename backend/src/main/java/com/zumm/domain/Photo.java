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
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.annotations.TenantId;

/**
 * Photo attachee a un objet du parc (US-010, US-028 ; elargie au SPRINT-21). On
 * stocke la reference ({@code url}) et une legende ; le binaire est hors base.
 * Entite sans {@code maj_le} (une photo ne se modifie pas), d'ou un mapping
 * autonome plutot que via {@link EntiteTenant}.
 *
 * <p><strong>Ce que le SPRINT-21 a change.</strong> {@code visite_id} etait
 * {@code NOT NULL} : une photo de ruche, de reine marquee, de cadre de recolte ou
 * de rucher n'avait aucun endroit ou aller — c'est le 🟡 du §1 de
 * {@code docs/ECART-CONCURRENTS.md}. Quatre rattachements s'ajoutent donc a la
 * visite, sans creer quatre tables.
 *
 * <p>L'invariant qui rend le modele sur : <strong>exactement une cible</strong>.
 * Une photo attachee a tout n'est attachee a rien, et une photo attachee a rien
 * est une fuite de stockage. La base le fait respecter
 * ({@code ck_photo_cible_unique}, {@code num_nonnulls(...) = 1}, etendu a six
 * cibles au SPRINT-28) ; la fabrique
 * {@link #sur(Cible, Object, String, String)} rend l'erreur impossible plus tot.
 */
@Entity
@Table(name = "photo")
public class Photo {

    /** Objets du parc auxquels une photo peut se rattacher. */
    public enum Cible {
        VISITE, RUCHE, SITE, REINE, RECOLTE, TRAITEMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visite_id")
    private Visite visite;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ruche_id")
    private Ruche ruche;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suivi_reine_id")
    private SuiviReine reine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recolte_id")
    private Recolte recolte;

    /**
     * Sixieme cible, ajoutee au SPRINT-28 : le scan de l'ordonnance
     * veterinaire. Le registre d'elevage devient verifiable quand la piece qui
     * l'autorise y est attachee, et non seulement referencee.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traitement_id")
    private Traitement traitement;

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

    /** Photo d'inspection : le cas d'origine (US-010), conserve tel quel. */
    public Photo(Visite visite, String url, String legende) {
        this.visite = visite;
        this.url = url;
        this.legende = legende;
    }

    /**
     * Photo rattachee a l'un des cinq objets possibles.
     *
     * <p>Une seule affectation a lieu, quel que soit le cas : c'est la traduction
     * en Java de {@code ck_photo_cible_unique}, et elle place l'invariant a la
     * construction plutot qu'a l'insertion.
     */
    public static Photo sur(Cible cible, Object porteur, String url, String legende) {
        Photo photo = new Photo();
        photo.url = url;
        photo.legende = legende;
        switch (cible) {
            case VISITE -> photo.visite = (Visite) porteur;
            case RUCHE -> photo.ruche = (Ruche) porteur;
            case SITE -> photo.site = (Site) porteur;
            case REINE -> photo.reine = (SuiviReine) porteur;
            case RECOLTE -> photo.recolte = (Recolte) porteur;
            case TRAITEMENT -> photo.traitement = (Traitement) porteur;
        }
        return photo;
    }

    /** Nature de l'objet auquel cette photo est attachee. */
    public Cible cible() {
        if (visite != null) {
            return Cible.VISITE;
        }
        if (ruche != null) {
            return Cible.RUCHE;
        }
        if (site != null) {
            return Cible.SITE;
        }
        if (reine != null) {
            return Cible.REINE;
        }
        if (recolte != null) {
            return Cible.RECOLTE;
        }
        return Cible.TRAITEMENT;
    }

    /** Identifiant de l'objet porteur, quelle que soit sa nature. */
    public Long cibleId() {
        return switch (cible()) {
            case VISITE -> visite.getId();
            case RUCHE -> ruche.getId();
            case SITE -> site.getId();
            case REINE -> reine.getId();
            case RECOLTE -> recolte.getId();
            case TRAITEMENT -> traitement.getId();
        };
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

    public Ruche getRuche() {
        return ruche;
    }

    public Site getSite() {
        return site;
    }

    public SuiviReine getReine() {
        return reine;
    }

    public Recolte getRecolte() {
        return recolte;
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
