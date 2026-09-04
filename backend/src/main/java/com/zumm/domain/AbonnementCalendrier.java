package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Jeton d'abonnement iCalendar (SPRINT-21).
 *
 * <p><strong>La seule entite metier du depot sans {@code @TenantId}</strong>, et
 * c'est sa fonction meme : elle est lue AVANT que le tenant soit connu, pour le
 * resoudre. Lui imposer le discriminant rendrait l'abonnement impossible a
 * servir — exactement le raisonnement qui met {@code SPRING_SESSION} hors
 * perimetre multi-tenant (migration V17).
 *
 * <p>Le champ {@link #tenantId} est donc une donnee PORTEE, pas un filtre : le
 * cloisonnement de cette table est applicatif, et tient en un seul endroit,
 * {@code AbonnementCalendrierService}, dont chaque requete cite le tenant.
 *
 * <p>Le jeton en clair n'est jamais stocke : {@link #jetonEmpreinte} porte son
 * SHA-256. Une fuite de la base ne rend donc aucune URL utilisable.
 */
@Entity
@Table(name = "abonnement_calendrier")
public class AbonnementCalendrier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @NotNull
    @Column(name = "agent_id", nullable = false, updatable = false)
    private Long agentId;

    @NotBlank
    @Size(max = 80)
    @Column(name = "libelle", nullable = false, length = 80)
    private String libelle;

    @NotBlank
    @Column(name = "jeton_empreinte", nullable = false, updatable = false, length = 64)
    private String jetonEmpreinte;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    @NotNull
    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(name = "revoque_le")
    private Instant revoqueLe;

    /**
     * Derniere fois que le jeton a servi.
     *
     * <p>Rendre l'usage VISIBLE est une mesure de securite a part entiere : un
     * abonnement qu'on croyait oublie et qui sert toutes les heures se remarque
     * dans l'ecran du compte, et se revoque.
     */
    @Column(name = "derniere_utilisation")
    private Instant derniereUtilisation;

    protected AbonnementCalendrier() {
        // Requis par JPA.
    }

    public AbonnementCalendrier(String tenantId, Long agentId, String libelle,
            String jetonEmpreinte, Instant expireLe) {
        this.tenantId = tenantId;
        this.agentId = agentId;
        this.libelle = libelle;
        this.jetonEmpreinte = jetonEmpreinte;
        this.expireLe = expireLe;
    }

    /** Utilisable : ni revoque, ni expire au moment demande. */
    public boolean utilisable(Instant instant) {
        return revoqueLe == null && expireLe.isAfter(instant);
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getJetonEmpreinte() {
        return jetonEmpreinte;
    }

    public Instant getCreeLe() {
        return creeLe;
    }

    public Instant getExpireLe() {
        return expireLe;
    }

    public Instant getRevoqueLe() {
        return revoqueLe;
    }

    public void revoquer(Instant instant) {
        this.revoqueLe = instant;
    }

    public Instant getDerniereUtilisation() {
        return derniereUtilisation;
    }

    public void marquerUtilise(Instant instant) {
        this.derniereUtilisation = instant;
    }
}
