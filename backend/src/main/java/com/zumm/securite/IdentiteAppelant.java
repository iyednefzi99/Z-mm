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
 */
public record IdentiteAppelant(String sujet, String email) {

    private static final IdentiteAppelant INCONNUE = new IdentiteAppelant(null, null);

    public static IdentiteAppelant de(Authentication authentification) {
        if (authentification == null) {
            return INCONNUE;
        }
        Object principal = authentification.getPrincipal();
        if (principal instanceof OidcUser utilisateur) {
            return new IdentiteAppelant(utilisateur.getSubject(), utilisateur.getEmail());
        }
        if (principal instanceof Jwt jeton) {
            return new IdentiteAppelant(jeton.getSubject(), jeton.getClaimAsString("email"));
        }
        return INCONNUE;
    }
}
