package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifie que l'API est fermee par defaut.
 *
 * <p>Le risque couvert est concret : un endpoint ajoute par inadvertance hors de
 * la liste d'exceptions doit exiger un jeton, jamais repondre en anonyme. Les
 * positions GPS des ruchers etant sensibles (risque de vol), un defaut « ouvert »
 * serait une fuite, pas une gene.
 *
 * <p>Depuis le SPRINT-12, deux garanties supplementaires sont eprouvees ici :
 * « authentifie » ne suffit plus (il faut un ROLE metier connu), et un jeton sans
 * rattachement a une exploitation est refuse au lieu de traverser la chaine pour
 * se heurter silencieusement a la RLS.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SecuriteApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("zumm/test-postgres:16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    @Autowired
    private MockMvc mockMvc;

    /** Jeton complet : un role metier ET un rattachement a une exploitation. */
    private static JwtRequestPostProcessor jetonComplet() {
        return jwt().jwt(b -> b.claim("tenant_id", "exploitation-test"))
                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    @Test
    @DisplayName("laisse passer la sonde de sante sans jeton")
    void laissePasserLaSondeDeSante() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("laisse passer l'identite publique sans jeton")
    void laissePasserIdentitePublique() throws Exception {
        mockMvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Zumm"));
    }

    @Test
    @DisplayName("refuse un endpoint non declare a un appelant anonyme")
    void refuseUnEndpointNonDeclareEnAnonyme() throws Exception {
        // Cet endpoint n'existe pas : la securite doit repondre AVANT le routage,
        // donc 401 et non 404 — preuve que le refus par defaut s'applique.
        mockMvc.perform(get("/api/ruchers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("laisse passer un jeton porteur d'un role metier et d'un tenant")
    void laissePasserUnJetonComplet() throws Exception {
        // Authentifie et habilite : la requete depasse la securite, 404 attendu
        // puisque l'endpoint n'existe pas.
        mockMvc.perform(get("/api/ruchers").with(jetonComplet()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("refuse un jeton authentifie mais sans role metier connu")
    void refuseUnJetonSansRoleMetier() throws Exception {
        // Regression du SPRINT-12 : `anyRequest().authenticated()` acceptait tout
        // porteur de jeton valide du royaume, y compris un compte de service ou un
        // utilisateur a qui aucun role Zumm n'a ete attribue.
        mockMvc.perform(get("/api/ruchers").with(
                        jwt().jwt(b -> b.claim("tenant_id", "exploitation-test"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("refuse un jeton sans claim tenant_id")
    void refuseUnJetonSansTenant() throws Exception {
        // Regression du SPRINT-12 : un mapper Keycloak manquant produisait des
        // jetons sans `tenant_id`. L'API paraissait fonctionner sur une base vide
        // (la RLS renvoyait zero ligne) au lieu de signaler la mauvaise
        // configuration. Elle doit desormais echouer franchement.
        mockMvc.perform(get("/api/sites").with(
                        jwt().authorities(new SimpleGrantedAuthority("ROLE_apiculteur"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("laisse le contrat OpenAPI public hors production")
    void laisseLeContratPublicHorsProduction() throws Exception {
        // Le profil `prod` bascule zumm.openapi.public a faux ; ce test tourne sans
        // ce profil, le contrat reste donc un livrable consultable.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
