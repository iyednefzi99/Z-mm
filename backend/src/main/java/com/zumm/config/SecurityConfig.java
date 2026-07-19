package com.zumm.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

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

    @Bean
    SecurityFilterChain chaineDeFiltres(HttpSecurity http) throws Exception {
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
                        // Tout le reste exige un jeton valide.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(convertisseurDeJeton())))
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
