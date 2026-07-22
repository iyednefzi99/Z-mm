package com.zumm.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Etablit le contexte tenant de la requete a partir du jeton JWT.
 *
 * <p>Le tenant est porte par le claim {@code tenant_id} (ADR-001 : realm Keycloak
 * unique, tenant en claim du jeton). Le filtre s'execute apres l'authentification
 * OAuth2, lit le claim de l'utilisateur courant, alimente {@link TenantContext},
 * puis <strong>l'efface systematiquement</strong> en fin de requete — un
 * {@code ThreadLocal} laisse en place fuiterait sur la requete suivante servie par
 * le meme thread.
 *
 * <p>Une requete non authentifiee (endpoints publics) ne fixe aucun tenant : tout
 * acces a une table metier renverrait alors zero ligne, ce qui est le comportement
 * voulu.
 */
public class TenantFilter extends OncePerRequestFilter {

    /** Nom du claim portant l'identifiant de tenant dans le jeton Keycloak. */
    public static final String CLAIM_TENANT = "tenant_id";

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
            FilterChain chaine) throws ServletException, IOException {
        try {
            extraireTenant().ifPresent(TenantContext::definir);
            chaine.doFilter(requete, reponse);
        } finally {
            TenantContext.effacer();
        }
    }

    private java.util.Optional<String> extraireTenant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof Jwt jeton) {
            return java.util.Optional.ofNullable(jeton.getClaimAsString(CLAIM_TENANT));
        }
        return java.util.Optional.empty();
    }
}
