package com.zumm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Invitation a rejoindre une exploitation (ADR-009, US-058).
 *
 * <p>C'est le support de la decision d'un responsable : « cette personne entre
 * dans mon exploitation, avec ce role ». Le code qu'elle porte est ce que le
 * futur utilisateur saisit a l'inscription, et c'est lui qui determine son
 * {@code tenant_id} — sans quoi le compte serait refuse par {@code TenantFilter}
 * sur chaque ecran.
 *
 * <p>La lecture faite a l'inscription ne passe PAS par cette entite : a ce
 * moment-la personne n'est authentifie, le tenant courant n'est pas pose, et la
 * politique RLS rend la table vide. Elle passe par
 * {@code com.zumm.repository.InvitationRepository}, qui appelle les fonctions
 * {@code SECURITY DEFINER} de la migration V18. Cette entite-ci ne sert qu'a la
 * GESTION des codes, par un utilisateur connecte de l'exploitation.
 */
@Entity
@Table(name = "code_invitation")
public class CodeInvitation extends EntiteTenant {

    /**
     * Alphabet du code : ni {@code O}/{@code 0}, ni {@code I}/{@code 1}, ni
     * {@code S}/{@code 5}. Un code se lit sur un papier ou s'epelle au telephone ;
     * les paires ambigues s'y payent en tentatives ratees.
     */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRTUVWXYZ2346789";

    private static final SecureRandom TIRAGE = new SecureRandom();

    @NotBlank
    @Size(max = 32)
    @Column(name = "code", nullable = false, length = 32, updatable = false)
    private String code;

    @NotBlank
    @Size(max = 20)
    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "utilisations_max", nullable = false)
    private int utilisationsMax;

    @Column(name = "utilisations", nullable = false, insertable = false, updatable = false)
    private int utilisations;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Size(max = 120)
    @Column(name = "cree_par", length = 120, updatable = false)
    private String creePar;

    protected CodeInvitation() {
        // Requis par JPA.
    }

    public CodeInvitation(String code, String role, int utilisationsMax, Instant expireLe,
            String creePar) {
        this.code = code;
        this.role = role;
        this.utilisationsMax = utilisationsMax;
        this.expireLe = expireLe;
        this.creePar = creePar;
    }

    /**
     * Tire un code lisible de la forme {@code ZM-4KTP-9RXA}.
     *
     * <p>{@link SecureRandom} et non {@code Random} : ce code est un secret de
     * courte duree, et un generateur previsible permettrait d'en fabriquer un
     * valide sans jamais avoir ete invite.
     */
    public static String tirerCode() {
        StringBuilder code = new StringBuilder("ZM-");
        for (int position = 0; position < 8; position++) {
            if (position == 4) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(TIRAGE.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Vrai si le code ne peut plus servir : perime ou toutes places prises. */
    public boolean estEpuise() {
        return expireLe.isBefore(Instant.now()) || utilisations >= utilisationsMax;
    }

    public String getCode() {
        return code;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getUtilisationsMax() {
        return utilisationsMax;
    }

    public void setUtilisationsMax(int utilisationsMax) {
        this.utilisationsMax = utilisationsMax;
    }

    public int getUtilisations() {
        return utilisations;
    }

    public Instant getExpireLe() {
        return expireLe;
    }

    public void setExpireLe(Instant expireLe) {
        this.expireLe = expireLe;
    }

    public String getCreePar() {
        return creePar;
    }
}
