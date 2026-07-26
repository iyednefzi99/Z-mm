package com.zumm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.util.StringUtils;

/**
 * Deconnexion propagee au fournisseur d'identite (RP-Initiated Logout).
 *
 * <p>Invalider la seule session locale ne suffit pas : la session SSO de Keycloak
 * resterait ouverte, et un simple retour sur la page reconnecterait
 * silencieusement l'utilisateur — qui croirait pourtant s'etre deconnecte. C'est
 * particulierement genant sur un poste partage entre plusieurs agents.
 *
 * <p>Pourquoi cette classe plutot que {@code OidcClientInitiatedLogoutSuccessHandler}
 * de Spring Security : celui-ci lit l'{@code end_session_endpoint} dans les
 * metadonnees de decouverte OIDC, lesquelles n'existent que si l'application
 * interroge le fournisseur AU DEMARRAGE. Zumm s'y refuse — le meme choix a deja
 * ete fait pour le serveur de ressources (cf. {@code docker-compose.yml}) : le
 * back-end ne doit pas dependre de la disponibilite de Keycloak pour demarrer.
 * L'URL de fin de session est donc construite a partir de l'emetteur, dont la
 * forme est fixee par la specification OpenID Connect.
 *
 * <p>Le {@code id_token_hint} evite a Keycloak de redemander a l'utilisateur
 * quelle session il souhaite clore.
 */
public class DeconnexionOidc implements LogoutSuccessHandler {

    private final String emetteur;
    private final String redirectionApres;

    public DeconnexionOidc(String emetteur, String redirectionApres) {
        this.emetteur = emetteur;
        this.redirectionApres = redirectionApres;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest requete, HttpServletResponse reponse,
            Authentication authentification) throws IOException {

        if (!StringUtils.hasText(emetteur)) {
            // Aucun fournisseur configure (poste de developpement) : la session
            // locale est deja close, on se contente de le dire.
            reponse.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        StringBuilder url = new StringBuilder(emetteur)
                .append("/protocol/openid-connect/logout?post_logout_redirect_uri=")
                .append(URLEncoder.encode(redirectionApres, StandardCharsets.UTF_8));

        if (authentification != null && authentification.getPrincipal() instanceof OidcUser utilisateur
                && utilisateur.getIdToken() != null) {
            url.append("&id_token_hint=")
                    .append(URLEncoder.encode(utilisateur.getIdToken().getTokenValue(),
                            StandardCharsets.UTF_8));
        }

        // 200 + l'URL dans le corps, et non une redirection 302 : la deconnexion
        // est declenchee par un `fetch` de la PWA, qui ne peut pas suivre une
        // redirection vers une autre origine. C'est au client de naviguer.
        reponse.setStatus(HttpServletResponse.SC_OK);
        reponse.setContentType("application/json");
        reponse.setCharacterEncoding("UTF-8");
        reponse.getWriter().write("{\"redirection\":\"" + url.toString().replace("\"", "\\\"") + "\"}");
    }
}
