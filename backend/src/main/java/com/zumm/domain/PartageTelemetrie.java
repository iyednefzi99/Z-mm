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
 * Jeton de partage d'un flux de mesures, borne a UNE ruche (SPRINT-26, lot F1).
 *
 * <p>Ferme le 🟡 du §5 : « le partage passe par l'appartenance au meme tenant ;
 * aucun partage inter-exploitations comme chez BeeLog Digital ». Le partage
 * INTERNE existait deja — c'est le tenant. Ce qui manquait etait de montrer une
 * courbe a quelqu'un du DEHORS : un mentor, un technicien sanitaire, un
 * groupement de developpement apicole.
 *
 * <p><strong>Meme forme que {@link AbonnementCalendrier}</strong>, et pour les
 * memes raisons : jeton de 256 bits, stocke en empreinte SHA-256, expiration
 * obligatoire, revocation, derniere utilisation visible. Comme lui, cette entite
 * PORTE le tenant sans le discriminer ({@code @TenantId} absent, pas de RLS) —
 * elle est lue AVANT que le tenant soit connu, puisque c'est le jeton qui le
 * resout. Son cloisonnement est donc <strong>applicatif</strong>, et tient dans
 * {@code PartageTelemetrieService} : c'est le point a auditer si cette table est
 * lue ailleurs.
 *
 * <p><strong>Une difference avec l'abonnement, deliberee : une seule ruche.</strong>
 * Un jeton qui ouvrirait l'exploitation entiere ne serait plus un partage, ce
 * serait un compte sans mot de passe. Et rien de ce qui sort par ce chemin ne
 * porte de position : le destinataire voit une courbe, jamais un rucher sur une
 * carte.
 */
@Entity
@Table(name = "partage_telemetrie")
public class PartageTelemetrie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @NotNull
    @Column(name = "ruche_id", nullable = false, updatable = false)
    private Long rucheId;

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
     * Dernier usage constate.
     *
     * <p>C'est ce qui rend la revocation decidable : un partage qui n'a jamais
     * servi se coupe sans hesiter, un partage consulte hier appartient a
     * quelqu'un qui s'en sert.
     */
    @Column(name = "derniere_utilisation")
    private Instant derniereUtilisation;

    protected PartageTelemetrie() {
        // Requis par JPA.
    }

    public PartageTelemetrie(String tenantId, Long rucheId, String libelle,
            String jetonEmpreinte, Instant expireLe) {
        this.tenantId = tenantId;
        this.rucheId = rucheId;
        this.libelle = libelle;
        this.jetonEmpreinte = jetonEmpreinte;
        this.expireLe = expireLe;
    }

    /** Utilisable : ni revoque, ni expire au moment demande. */
    public boolean utilisable(Instant instant) {
        return revoqueLe == null && expireLe.isAfter(instant);
    }

    public void revoquer(Instant instant) {
        this.revoqueLe = instant;
    }

    public void marquerUtilise(Instant instant) {
        this.derniereUtilisation = instant;
    }

    public Long getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public Long getRucheId() {
        return rucheId;
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

    public Instant getDerniereUtilisation() {
        return derniereUtilisation;
    }
}
