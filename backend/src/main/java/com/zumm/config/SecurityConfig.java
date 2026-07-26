package com.zumm.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import com.zumm.tenant.TenantFilter;
import com.zumm.web.FiltreIdempotence;
import com.zumm.web.MagasinIdempotence;

/**
 * Securite de l'API : serveur de ressources OAuth2 valide par Keycloak.
 *
 * <p>L'API ne detient aucun mot de passe et n'ouvre aucune session : elle valide
 * un jeton JWT emis par Keycloak a chaque requete. Les roles sont portes par le
 * jeton et convertis en autorites Spring Security.
 *
 * <p>La matrice RBAC complete (US-005, US-022) sera declinee au SPRINT-01, une
 * fois les profils arretes depuis le cahier des charges. Ici, on pose seulement
 * la regle de fond : tout est refuse par defaut, sauf ce qui est explicitement
 * ouvert.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Prefixe attendu par Spring Security pour une autorite de role. */
    private static final String PREFIXE_ROLE = "ROLE_";

    /**
     * Decodeur de jetons, redefini pour AJOUTER la validation d'audience (SPRINT-12)
     * aux validations par defaut de Spring Boot (signature, expiration, emetteur).
     *
     * <p>La construction reprend la logique de l'auto-configuration : {@code jwk-set-uri}
     * quand il est fourni (aucun appel au demarrage, cf. docker-compose.yml), sinon
     * decouverte OIDC depuis l'emetteur. L'emetteur n'est valide que s'il est
     * renseigne — les tests le laissent vide et fournissent le seul JWKS.
     *
     * @param audience audience attendue ; vide desactive la verification (repli
     *                 d'exploitation, a n'utiliser que le temps d'un incident)
     */
    @Bean
    JwtDecoder decodeurDeJeton(OAuth2ResourceServerProperties proprietes,
            @Value("${zumm.oidc.audience:zumm-backend}") String audience) {

        OAuth2ResourceServerProperties.Jwt jwt = proprietes.getJwt();
        String jwkSetUri = jwt.getJwkSetUri();
        String emetteur = jwt.getIssuerUri();

        NimbusJwtDecoder decodeur = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(emetteur);

        List<OAuth2TokenValidator<Jwt>> validateurs = new java.util.ArrayList<>();
        validateurs.add(StringUtils.hasText(emetteur)
                ? JwtValidators.createDefaultWithIssuer(emetteur)
                : JwtValidators.createDefault());
        if (StringUtils.hasText(audience)) {
            validateurs.add(new ValidateurAudience(audience));
        }
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validateurs));
        return decodeur;
    }

    @Bean
    SecurityFilterChain chaineDeFiltres(HttpSecurity http,
            MagasinIdempotence magasinIdempotence,
            @Value("${zumm.openapi.public:true}") boolean contratPublic) throws Exception {
        http
                // API sans etat : aucun cookie de session, donc pas de CSRF a proteger.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requetes -> requetes
                        // Sonde de vie : indispensable a l'orchestrateur, sans detail expose.
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Identite de l'application : page d'accueil publique.
                        .requestMatchers(HttpMethod.GET, "/api/info").permitAll()

                        // ── Matrice RBAC (US-022), derivee des roles du cahier ──
                        // Le journal d'audit (US-043) est reserve au pilotage :
                        // responsable et administrateur uniquement.
                        .requestMatchers(HttpMethod.GET, "/api/audit", "/api/audit/**")
                        .hasAnyRole("responsable", "admin")
                        // L'approbation d'un planning est reservee au superviseur
                        // (et au-dessus) : c'est sa fonction propre (cahier, chap. 4).
                        .requestMatchers(HttpMethod.POST,
                                "/api/plannings/*/approuver", "/api/plannings/*/refuser")
                        .hasAnyRole("superviseur", "responsable", "admin")
                        // Le referentiel (fermier, ferme, site, agent, ruche) et la
                        // configuration sont geres par le responsable / administrateur ;
                        // les autres roles y ont un acces en LECTURE seule.
                        .requestMatchers(HttpMethod.POST, "/api/fermiers/**", "/api/fermes/**",
                                "/api/sites/**", "/api/agents/**", "/api/ruches/**")
                        .hasAnyRole("responsable", "admin")
                        .requestMatchers(HttpMethod.PUT, "/api/fermiers/**", "/api/fermes/**",
                                "/api/sites/**", "/api/agents/**", "/api/ruches/**")
                        .hasAnyRole("responsable", "admin")
                        .requestMatchers(HttpMethod.DELETE, "/api/fermiers/**", "/api/fermes/**",
                                "/api/sites/**", "/api/agents/**", "/api/ruches/**")
                        .hasAnyRole("responsable", "admin")

                        // ── Identite machine (SPRINT-12) ──
                        // Une passerelle IoT n'est pas un utilisateur : elle porte le
                        // role `capteur` via un compte de service (client_credentials)
                        // et n'a le droit QUE de deposer des mesures. Elle est exclue
                        // de tout le reste par la regle terminale ci-dessous, qui exige
                        // un role humain.
                        .requestMatchers(HttpMethod.POST, "/api/mesures")
                        .hasAnyRole("capteur", "apiculteur", "superviseur", "responsable", "admin")

                        // Contrat OpenAPI 3 (US-026) et son explorateur. PUBLICS hors
                        // production seulement : en production, publier la cartographie
                        // complete de l'API a l'anonyme sert surtout la reconnaissance
                        // d'un attaquant (cf. zumm.openapi.public, faux sous le profil
                        // `prod`), les partenaires recevant le contrat hors ligne.
                        .requestMatchers(HttpMethod.GET,
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .access((authentification, contexte) -> new AuthorizationDecision(
                                contratPublic || estAdministrateur(authentification.get())))

                        // Regle terminale : refus par defaut. Le reste (plannings hors
                        // decision, visites, photos, lectures) est ouvert a tout role
                        // METIER — l'apiculteur planifie, visite et remplit ses rapports —
                        // mais « authentifie » ne suffit pas : un jeton sans role connu,
                        // ou porteur du seul role machine, n'a rien a faire ici.
                        .anyRequest()
                        .hasAnyRole("apiculteur", "superviseur", "responsable", "admin"))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(convertisseurDeJeton())))
                // Le contexte tenant se lit sur le jeton : le filtre vient donc
                // apres l'authentification, une fois le JWT resolu.
                .addFilterAfter(new TenantFilter(), BasicAuthenticationFilter.class)
                // L'idempotence vient APRES le tenant : le magasin ecrit dans une
                // table sous RLS, donc il lui faut un tenant deja pose. Elle vient
                // aussi avant les controleurs, pour pouvoir court-circuiter le
                // traitement quand la reponse est deja connue.
                .addFilterAfter(new FiltreIdempotence(magasinIdempotence), TenantFilter.class)
                .headers(entetes -> entetes
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        // HSTS : le TLS est termine par le proxy inverse, mais l'en-tete
                        // doit etre emis par l'application.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)));

        return http.build();
    }

    /** Un administrateur garde l'acces au contrat OpenAPI, y compris en production. */
    private static boolean estAdministrateur(Authentication authentification) {
        return authentification != null
                && authentification.isAuthenticated()
                && authentification.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch((PREFIXE_ROLE + "admin")::equals);
    }

    /**
     * Convertit les roles Keycloak en autorites Spring Security.
     *
     * <p>Keycloak place les roles de royaume sous {@code realm_access.roles} et les
     * roles de client sous {@code resource_access.<client>.roles} : ni l'un ni
     * l'autre n'est lu par defaut par Spring Security, d'ou ce convertisseur.
     */
    private JwtAuthenticationConverter convertisseurDeJeton() {
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter(ConvertisseurDeRoles::extraire);
        return convertisseur;
    }

    /** Extraction des roles de royaume Keycloak, isolee pour rester testable. */
    static final class ConvertisseurDeRoles {

        private ConvertisseurDeRoles() {
        }

        @SuppressWarnings("unchecked")
        static Collection<GrantedAuthority> extraire(Jwt jeton) {
            // Portees standard (scope/scp), comportement par defaut de Spring.
            Collection<GrantedAuthority> autorites =
                    new java.util.ArrayList<>(new JwtGrantedAuthoritiesConverter().convert(jeton));

            Map<String, Object> accesRoyaume = jeton.getClaim("realm_access");
            if (accesRoyaume == null) {
                return autorites;
            }

            Object roles = accesRoyaume.get("roles");
            if (roles instanceof Collection<?> liste) {
                ((Collection<Object>) liste).stream()
                        .map(String::valueOf)
                        .map(role -> new SimpleGrantedAuthority(PREFIXE_ROLE + role))
                        .forEach(autorites::add);
            }
            return autorites;
        }
    }

    /** Types exposes pour les tests ; evite de dupliquer les litteraux. */
    static Set<String> rolesDe(AbstractAuthenticationToken jeton) {
        return jeton.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autorite -> autorite.startsWith(PREFIXE_ROLE))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Roles metier connus a ce stade ; la matrice complete arrive au SPRINT-01. */
    static final List<String> ROLES_ATTENDUS =
            List.of("apiculteur", "superviseur", "responsable", "admin");
}
