package com.zumm.config;

import com.zumm.securite.FiltrePortee;
import com.zumm.securite.ResolveurPortee;
import com.zumm.tenant.TenantFilter;
import com.zumm.web.FiltreIdempotence;
import com.zumm.web.MagasinIdempotence;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * Securite de l'API — DEUX chemins d'authentification, un seul jeu de regles.
 *
 * <p>Depuis la mise en oeuvre de l'ADR-006, Zumm distingue deux populations
 * d'appelants, qui n'ont ni les memes contraintes ni les memes risques :
 *
 * <ul>
 *   <li><strong>les machines</strong> — passerelles de capteurs, integrations
 *       tierces — presentent un jeton porteur. Elles n'ont pas de navigateur,
 *       donc pas de cookie, donc aucun risque de requete intersite : la chaine
 *       est sans etat et CSRF y est sans objet ;
 *   <li><strong>les navigateurs</strong> ne recoivent plus aucun jeton. Le
 *       back-end mene lui-meme le flux OIDC et garde les jetons cote serveur ;
 *       le navigateur ne detient qu'un cookie de session {@code HttpOnly}. Une
 *       XSS ne peut alors plus exfiltrer de session reutilisable ailleurs — mais
 *       le navigateur envoie ce cookie tout seul, donc <strong>CSRF redevient
 *       necessaire</strong>.
 * </ul>
 *
 * <p>Le point delicat d'une telle separation est la DERIVE : deux chaines, deux
 * matrices d'autorisation, et un jour l'une des deux oublie une regle. La matrice
 * est donc ecrite <strong>une fois</strong>, dans {@link #matriceRbac}, et
 * appliquee aux deux. Un test le verifie sur les deux chemins.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Prefixe attendu par Spring Security pour une autorite de role. */
    private static final String PREFIXE_ROLE = "ROLE_";

    /** Roles humains. Un jeton sans l'un d'eux n'atteint aucun endpoint metier. */
    private static final String[] ROLES_METIER = {"apiculteur", "superviseur", "responsable", "admin"};

    /**
     * Reconnait un appelant machine : il presente un jeton porteur.
     *
     * <p>C'est le critere d'aiguillage entre les deux chaines. Il est explicite et
     * observable — pas de devinette sur l'agent utilisateur ni sur le chemin.
     */
    private static final RequestMatcher PORTEUR_DE_JETON = (HttpServletRequest requete) -> {
        String entete = requete.getHeader("Authorization");
        return entete != null && entete.startsWith("Bearer ");
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Chaine 1 — machines : jeton porteur, sans etat
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    @Order(1)
    SecurityFilterChain chaineJetonPorteur(HttpSecurity http,
            MagasinIdempotence magasinIdempotence,
            ResolveurPortee resolveurPortee,
            @Value("${zumm.openapi.public:true}") boolean contratPublic) throws Exception {
        http
                .securityMatcher(PORTEUR_DE_JETON)
                // Aucun cookie n'est emis ni lu : le navigateur n'envoie rien tout
                // seul, il n'y a donc pas de requete intersite a forger.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requetes -> matriceRbac(requetes, contratPublic))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(convertisseurDeJeton())));

        appliquerCommun(http, magasinIdempotence, resolveurPortee);
        return http.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chaine 2 — navigateurs : session cookie, jetons cotes serveur (BFF)
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    @Order(2)
    SecurityFilterChain chaineNavigateur(HttpSecurity http,
            MagasinIdempotence magasinIdempotence,
            ResolveurPortee resolveurPortee,
            @Value("${zumm.openapi.public:true}") boolean contratPublic,
            @Value("${ZUMM_OIDC_ISSUER_URI:}") String emetteur,
            @Value("${zumm.bff.apres-deconnexion:/}") String apresDeconnexion) throws Exception {
        http
                // CSRF ACTIF, et c'est la contrepartie directe du cookie : le
                // navigateur l'envoie sans qu'on le lui demande, donc un site tiers
                // pourrait declencher une mutation. Le jeton est depose dans un
                // cookie LISIBLE par le script de la page (`withHttpOnlyFalse`) —
                // ce n'est pas un secret, c'est une preuve que la requete vient
                // bien de notre page, laquelle est seule a pouvoir le relire grace
                // a la politique d'origine.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(requetes -> {
                    // On ne demande pas d'etre connecte pour se connecter. Ces
                    // trois routes sont donc ouvertes — CSRF, lui, reste exige :
                    // ce sont des mutations sur une origine a cookie (ADR-009).
                    requetes.requestMatchers("/bff/session", "/bff/connexion", "/bff/inscription")
                            .permitAll();
                    matriceRbac(requetes, contratPublic);
                })
                .oauth2Login(connexion -> connexion
                        .userInfoEndpoint(infos -> infos.userAuthoritiesMapper(convertisseurDeRolesOidc()))
                        // Apres connexion, on revient a la racine : la PWA restaure
                        // elle-meme la route quittee (US-051).
                        .defaultSuccessUrl("/", true))
                .logout(deconnexion -> deconnexion
                        .logoutUrl("/bff/deconnexion")
                        // Deconnexion PROPAGEE a Keycloak : effacer la seule session
                        // locale laisserait la session SSO ouverte, et un simple
                        // retour sur la page reconnecterait silencieusement.
                        .logoutSuccessHandler(new DeconnexionOidc(emetteur, apresDeconnexion))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                // Une requete d'API non authentifiee doit recevoir 401, pas une
                // redirection vers Keycloak : c'est le client qui decide d'aller se
                // connecter, pas le navigateur au milieu d'un appel `fetch`.
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                                new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**")));

        appliquerCommun(http, magasinIdempotence, resolveurPortee);
        return http.build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Regles partagees
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Matrice RBAC (US-022), ecrite UNE fois et appliquee a chaque chaine.
     *
     * <p>La dupliquer serait la garantie qu'elles divergent : une regle ajoutee
     * d'un cote, oubliee de l'autre, et le chemin oublie devient la porte
     * d'entree.
     */
    private void matriceRbac(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry requetes,
            boolean contratPublic) {
        requetes
                // Sonde de vie : indispensable a l'orchestrateur, sans detail expose.
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                .permitAll()
                // Identite de l'application : page d'accueil publique.
                .requestMatchers(HttpMethod.GET, "/api/info").permitAll()

                // Le journal d'audit (US-043) est reserve au pilotage.
                .requestMatchers(HttpMethod.GET, "/api/audit", "/api/audit/**")
                .hasAnyRole("responsable", "admin")
                // Les codes d'invitation (US-058) decident QUI entre dans
                // l'exploitation et avec quel role : c'est une prerogative de
                // responsable. En lecture aussi — la liste des codes en cours est
                // une liste de clefs valides.
                .requestMatchers("/api/invitations", "/api/invitations/**")
                .hasAnyRole("responsable", "admin")
                // L'approbation d'un planning est la fonction propre du superviseur.
                .requestMatchers(HttpMethod.POST,
                        "/api/plannings/*/approuver", "/api/plannings/*/refuser")
                .hasAnyRole("superviseur", "responsable", "admin")
                // Le referentiel et la configuration sont geres par le responsable
                // et l'administrateur ; les autres roles y ont un acces en LECTURE.
                .requestMatchers(HttpMethod.POST, "/api/fermiers/**", "/api/fermes/**",
                        "/api/sites/**", "/api/agents/**", "/api/ruches/**")
                .hasAnyRole("responsable", "admin")
                .requestMatchers(HttpMethod.PUT, "/api/fermiers/**", "/api/fermes/**",
                        "/api/sites/**", "/api/agents/**", "/api/ruches/**")
                .hasAnyRole("responsable", "admin")
                .requestMatchers(HttpMethod.DELETE, "/api/fermiers/**", "/api/fermes/**",
                        "/api/sites/**", "/api/agents/**", "/api/ruches/**")
                .hasAnyRole("responsable", "admin")

                // Identite machine : une passerelle IoT depose des mesures, et rien
                // d'autre. La regle terminale l'exclut de tout le reste.
                .requestMatchers(HttpMethod.POST, "/api/mesures")
                .hasAnyRole("capteur", "apiculteur", "superviseur", "responsable", "admin")

                // Contrat OpenAPI : public hors production seulement.
                .requestMatchers(HttpMethod.GET,
                        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .access((authentification, contexte) -> new AuthorizationDecision(
                        contratPublic || estAdministrateur(authentification.get())))

                // Refus par defaut : « authentifie » ne suffit pas, il faut un role
                // metier connu.
                .anyRequest().hasAnyRole(ROLES_METIER);
    }

    /** Filtres et en-tetes communs aux deux chaines. */
    private void appliquerCommun(HttpSecurity http, MagasinIdempotence magasinIdempotence,
            ResolveurPortee resolveurPortee) throws Exception {
        http
                // Le contexte tenant se lit sur l'identite : le filtre vient donc
                // apres l'authentification, jeton ou session.
                .addFilterAfter(new TenantFilter(), BasicAuthenticationFilter.class)
                // La portee (US-057) vient APRES le tenant : resoudre l'agent
                // suppose une lecture en base, donc une exploitation deja fixee.
                .addFilterAfter(new FiltrePortee(resolveurPortee), TenantFilter.class)
                // L'idempotence vient en dernier : le magasin ecrit dans une table
                // sous RLS, il lui faut tenant ET portee deja poses.
                .addFilterAfter(new FiltreIdempotence(magasinIdempotence), FiltrePortee.class)
                .headers(entetes -> entetes
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        // HSTS : le TLS est termine par le proxy inverse, mais
                        // l'en-tete doit etre emis par l'application.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000)));
    }

    /**
     * Decodeur de jetons, redefini pour AJOUTER la validation d'audience aux
     * validations par defaut de Spring Boot (signature, expiration, emetteur).
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

    /**
     * Autorites portees par un jeton, pour les appelants qui n'ont pas de chaine
     * de filtres pour les leur poser.
     *
     * <p>Ouverte au seul {@code IdentiteBffController}, qui etablit une session a
     * partir d'un jeton obtenu en arriere-plan (ADR-009). Il lui faut LES MEMES
     * autorites que la chaine de filtres, et refaire l'extraction chez lui serait
     * le debut de la derive que cette classe s'attache a eviter : deux lectures
     * de {@code realm_access}, dont une qu'on oublie de corriger.
     */
    public static Collection<GrantedAuthority> autoritesDeJeton(Jwt jeton) {
        return ConvertisseurDeRoles.extraire(jeton);
    }

    /** Un administrateur garde l'acces au contrat OpenAPI, y compris en production. */
    private static boolean estAdministrateur(Authentication authentification) {
        return authentification != null
                && authentification.isAuthenticated()
                && authentification.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch((PREFIXE_ROLE + "admin")::equals);
    }

    /** Convertit les roles Keycloak d'un JETON en autorites Spring Security. */
    private JwtAuthenticationConverter convertisseurDeJeton() {
        JwtAuthenticationConverter convertisseur = new JwtAuthenticationConverter();
        convertisseur.setJwtGrantedAuthoritiesConverter(ConvertisseurDeRoles::extraire);
        return convertisseur;
    }

    /**
     * Convertit les roles Keycloak d'une SESSION OIDC en autorites.
     *
     * <p>Meme source (`realm_access.roles`), meme extraction : c'est le meme
     * royaume qui les emet. Seul le contenant change — un jeton d'identite plutot
     * qu'un jeton d'acces.
     */
    private GrantedAuthoritiesMapper convertisseurDeRolesOidc() {
        return autorites -> {
            Set<GrantedAuthority> resultat = new java.util.LinkedHashSet<>(autorites);
            autorites.stream()
                    .filter(OidcUserAuthority.class::isInstance)
                    .map(OidcUserAuthority.class::cast)
                    .forEach(autorite -> resultat.addAll(
                            ConvertisseurDeRoles.depuisAccesRoyaume(
                                    autorite.getIdToken().getClaimAsMap("realm_access"))));
            return resultat;
        };
    }

    /** Extraction des roles de royaume Keycloak, isolee pour rester testable. */
    static final class ConvertisseurDeRoles {

        private ConvertisseurDeRoles() {
        }

        static Collection<GrantedAuthority> extraire(Jwt jeton) {
            // Portees standard (scope/scp), comportement par defaut de Spring.
            Collection<GrantedAuthority> autorites =
                    new java.util.ArrayList<>(new JwtGrantedAuthoritiesConverter().convert(jeton));
            autorites.addAll(depuisAccesRoyaume(jeton.getClaimAsMap("realm_access")));
            return autorites;
        }

        /**
         * Roles portes par le claim {@code realm_access}, quel que soit le jeton
         * qui le transporte. Keycloak le place sous {@code realm_access.roles} ;
         * Spring Security ne le lit pas de lui-meme, d'ou cette extraction.
         */
        @SuppressWarnings("unchecked")
        static Collection<GrantedAuthority> depuisAccesRoyaume(Map<String, Object> accesRoyaume) {
            if (accesRoyaume == null) {
                return List.of();
            }
            Object roles = accesRoyaume.get("roles");
            if (!(roles instanceof Collection<?> liste)) {
                return List.of();
            }
            return ((Collection<Object>) liste).stream()
                    .map(String::valueOf)
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(PREFIXE_ROLE + role))
                    .toList();
        }
    }

    /** Types exposes pour les tests ; evite de dupliquer les litteraux. */
    static Set<String> rolesDe(AbstractAuthenticationToken jeton) {
        return jeton.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autorite -> autorite.startsWith(PREFIXE_ROLE))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Roles metier connus ; la matrice complete est dans {@link #matriceRbac}. */
    static final List<String> ROLES_ATTENDUS = List.of(ROLES_METIER);
}
