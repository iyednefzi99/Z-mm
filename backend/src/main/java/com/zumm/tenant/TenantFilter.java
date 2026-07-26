package com.zumm.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 *
 * <p><strong>Un jeton JWT SANS claim {@code tenant_id} est refuse en 403</strong>
 * (SPRINT-12). Auparavant, un tel jeton traversait la chaine et se heurtait a la
 * RLS, qui renvoyait zero ligne : l'API paraissait fonctionner sur une base vide,
 * et un mapper Keycloak manquant restait invisible pendant des semaines. Un refus
 * explicite fait echouer la mauvaise configuration a la premiere requete, au lieu
 * de la deguiser en resultat vide.
 */
public class TenantFilter extends OncePerRequestFilter {

    /** Nom du claim portant l'identifiant de tenant dans le jeton Keycloak. */
    public static final String CLAIM_TENANT = "tenant_id";

    /**
     * Chemins publics, exclus du filtre : ils ne touchent aucune donnee metier et
     * doivent repondre qu'un jeton soit presente ou non. Sans cette exclusion, une
     * sonde de sante appelee avec un jeton mal configure repondrait 403 et ferait
     * croire l'application en panne.
     */
    private static final String[] CHEMINS_PUBLICS = {
        "/actuator/", "/api/info", "/v3/api-docs", "/swagger-ui"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        String chemin = requete.getRequestURI();
        for (String prefixe : CHEMINS_PUBLICS) {
            if (chemin.startsWith(prefixe)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
            FilterChain chaine) throws ServletException, IOException {
        Optional<Jwt> jeton = jetonCourant();
        if (jeton.isPresent()) {
            String tenant = jeton.get().getClaimAsString(CLAIM_TENANT);
            if (tenant == null || tenant.isBlank()) {
                refuser(reponse);
                return;
            }
            TenantContext.definir(tenant);
        }
        try {
            chaine.doFilter(requete, reponse);
        } finally {
            TenantContext.effacer();
        }
    }

    /**
     * Refus en 403 au format ProblemDetail (RFC 7807), comme le reste de l'API. Le
     * message reste generique : il s'adresse a l'exploitant, pas a l'appelant, et
     * ne doit rien reveler de la configuration du fournisseur d'identite.
     */
    private void refuser(HttpServletResponse reponse) throws IOException {
        reponse.setStatus(HttpStatus.FORBIDDEN.value());
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        reponse.getWriter().write("""
                {"type":"about:blank","title":"Forbidden","status":403,\
                "detail":"Jeton sans rattachement a une exploitation."}""");
    }

    private Optional<Jwt> jetonCourant() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification != null && authentification.getPrincipal() instanceof Jwt jeton) {
            return Optional.of(jeton);
        }
        return Optional.empty();
    }
}
