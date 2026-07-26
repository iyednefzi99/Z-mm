package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Backend-for-Frontend (ADR-006) : le navigateur ne detient plus de jeton.
 *
 * <p>Ce que ces tests protegent, dans l'ordre d'importance :
 *
 * <ol>
 *   <li><strong>CSRF mord reellement</strong> sur le chemin cookie. C'est la
 *       contrepartie obligatoire du cookie de session : sans elle, le BFF
 *       remplace un risque d'exfiltration par un risque de requete forgee, ce qui
 *       n'est pas un progres ;
 *   <li>le chemin JETON PORTEUR reste sans etat et sans CSRF — une passerelle IoT
 *       n'a pas de navigateur, donc rien a forger ;
 *   <li>la matrice RBAC s'applique <strong>identiquement</strong> aux deux
 *       chemins. Deux chaines, c'est deux occasions de diverger ;
 *   <li>le tenant est resolu depuis la session OIDC comme depuis le jeton.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BffSessionIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("zumm/test-postgres:16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    private static final String TENANT = "exploitation-bff";

    @Autowired
    private MockMvc mockMvc;

    /** Session de navigateur : identite OIDC, tenant dans le jeton d'identite. */
    private static org.springframework.security.test.web.servlet.request
            .SecurityMockMvcRequestPostProcessors.OidcLoginRequestPostProcessor sessionNavigateur(
            String... roles) {
        var autorites = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toArray(SimpleGrantedAuthority[]::new);
        return oidcLogin()
                .idToken(jeton -> jeton
                        .claim("tenant_id", TENANT)
                        .claim("preferred_username", "agent-bff"))
                .authorities(autorites);
    }

    // ─── Session ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sans session, /bff/session repond 401 et non un corps vide")
    void sansSession() throws Exception {
        // 401 permet a la PWA de distinguer « pas connecte » de « connecte sans
        // role » : deux situations, deux ecrans.
        mockMvc.perform(get("/bff/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("avec session, /bff/session rend l'identite, les roles et l'exploitation")
    void avecSession() throws Exception {
        mockMvc.perform(get("/bff/session").with(sessionNavigateur("apiculteur", "superviseur")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utilisateur").value("agent-bff"))
                .andExpect(jsonPath("$.exploitation").value(TENANT))
                .andExpect(jsonPath("$.roles.length()").value(2))
                .andExpect(jsonPath("$.roles[0]").value("apiculteur"));
    }

    @Test
    @DisplayName("la session ne divulgue aucun jeton au navigateur")
    void aucunJetonDivulgue() throws Exception {
        // Le coeur de l'ADR-006 : ce qui sort d'ici est lisible par un script de
        // la page. Un jeton qui y figurerait annulerait tout le benefice.
        String corps = mockMvc.perform(get("/bff/session").with(sessionNavigateur("apiculteur")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(corps)
                .doesNotContain("token")
                .doesNotContain("jeton")
                .doesNotContain("Bearer");
    }

    // ─── CSRF : la contrepartie du cookie ───────────────────────────────────

    @Test
    @DisplayName("une mutation par session SANS jeton CSRF est refusee")
    void mutationSansCsrfRefusee() throws Exception {
        // Le test qui compte. Si celui-ci passe au vert en supprimant la
        // configuration CSRF, c'est que la protection n'existe pas.
        mockMvc.perform(post("/api/fermiers")
                        .with(sessionNavigateur("responsable"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Sans jeton CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la meme mutation avec jeton CSRF aboutit")
    void mutationAvecCsrfAcceptee() throws Exception {
        mockMvc.perform(post("/api/fermiers")
                        .with(sessionNavigateur("responsable"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Avec jeton CSRF\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("une lecture par session n'exige pas de jeton CSRF")
    void lectureSansCsrf() throws Exception {
        // CSRF ne protege que ce qui CHANGE l'etat : l'imposer aux lectures
        // n'ajouterait aucune securite et casserait la navigation.
        mockMvc.perform(get("/api/fermiers").with(sessionNavigateur("apiculteur")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("un en-tete Bearer aiguille vers la chaine sans etat, qui valide le jeton")
    void enTeteBearerAiguilleVersLaChaineSansEtat() throws Exception {
        // Preuve du bon aiguillage : avec un en-tete porteur, c'est la chaine 1
        // qui traite la requete — elle tente de decoder le jeton et le refuse en
        // 401. Une session de navigateur, elle, aurait recu 403 faute de jeton
        // CSRF : les deux codes distinguent les deux chaines sans ambiguite.
        //
        // On ne peut pas forger ici un jeton VALIDE sans cle de signature ; ce
        // que ce test etablit, c'est le routage et le fait que la chaine porteuse
        // ne laisse pas passer un jeton non verifiable.
        mockMvc.perform(post("/api/fermiers")
                        .header("Authorization", "Bearer jeton-non-signe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Par jeton porteur\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ─── La matrice ne doit pas diverger entre les deux chaines ─────────────

    @Test
    @DisplayName("le refus par defaut s'applique aussi a une session sans role metier")
    void sessionSansRoleMetierRefusee() throws Exception {
        mockMvc.perform(get("/api/fermiers").with(sessionNavigateur()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("l'ecriture du referentiel est refusee a l'apiculteur, par session comme par jeton")
    void memeMatriceSurLesDeuxChemins() throws Exception {
        mockMvc.perform(post("/api/fermiers")
                        .with(sessionNavigateur("apiculteur")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Refuse par session\"}"))
                .andExpect(status().isForbidden());

        // Meme regle, identite portee par un jeton plutot que par une session.
        mockMvc.perform(post("/api/fermiers")
                        .with(jwt().jwt(b -> b.claim("tenant_id", TENANT))
                                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Refuse par jeton\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("une session sans tenant est refusee, comme un jeton sans tenant")
    void sessionSansTenantRefusee() throws Exception {
        // Meme garde que celle posee au SPRINT-12 sur le jeton : une identite sans
        // rattachement a une exploitation doit echouer franchement, pas se
        // heurter a la RLS et rendre zero ligne.
        mockMvc.perform(get("/api/fermiers")
                        .with(oidcLogin()
                                .idToken(jeton -> jeton.claim("preferred_username", "sans-tenant"))
                                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur"))))
                .andExpect(status().isForbidden());
    }
}
