package com.zumm.securite;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Identite de l'appelant, quelle que soit la chaine qui l'a authentifie.
 *
 * <p>Depuis le BFF (ADR-006), une meme identite peut arriver par deux porteurs :
 * un {@link Jwt} pour les machines, un {@link OidcUser} pour les navigateurs.
 * Chaque lecteur de claims qui distinguerait les deux dupliquerait la meme paire
 * de {@code instanceof} — et un jour l'un des deux serait oublie. Cette classe
 * est ce point unique.
 *
 * @param sujet claim {@code sub}, identifiant stable chez le fournisseur
 * @param email claim {@code email}, cle de premiere liaison uniquement
 * @param nom   claim {@code preferred_username}, destine a l'AFFICHAGE et au
 *              journal d'audit — jamais a une decision d'autorisation : il est
 *              modifiable dans le royaume, contrairement au sujet
 */
public record IdentiteAppelant(String sujet, String email, String nom) {

    /** Acteur inscrit au journal hors contexte authentifie (tache planifiee, amorcage). */
    public static final String ACTEUR_SYSTEME = "systeme";

    private static final String CLAIM_NOM = "preferred_username";

    private static final IdentiteAppelant INCONNUE = new IdentiteAppelant(null, null, null);

    public static IdentiteAppelant de(Authentication authentification) {
        if (authentification == null) {
            return INCONNUE;
        }
        Object principal = authentification.getPrincipal();
        if (principal instanceof OidcUser utilisateur) {
            return new IdentiteAppelant(utilisateur.getSubject(), utilisateur.getEmail(),
                    utilisateur.getClaimAsString(CLAIM_NOM));
        }
        if (principal instanceof Jwt jeton) {
            return new IdentiteAppelant(jeton.getSubject(), jeton.getClaimAsString("email"),
                    jeton.getClaimAsString(CLAIM_NOM));
        }
        return INCONNUE;
    }

    /**
     * Nom lisible pour le journal d'audit.
     *
     * <p>Le repli n'est PAS anodin : sans lui, une session de navigateur inscrirait
     * l'UUID du sujet la ou un appelant machine inscrit son nom d'utilisateur. Le
     * journal deviendrait illisible pour la population qui l'alimente le plus, et
     * la corelation entre deux entrees du meme acteur exigerait une jointure que
     * personne ne ferait au moment ou l'on consulte un audit — c'est-a-dire pendant
     * un incident.
     *
     * @return {@code preferred_username}, a defaut le sujet, a defaut
     *         {@value #ACTEUR_SYSTEME}
     */
    public String nomPourAudit() {
        if (nom != null && !nom.isBlank()) {
            return nom;
        }
        if (sujet != null && !sujet.isBlank()) {
            return sujet;
        }
        return ACTEUR_SYSTEME;
    }
}
