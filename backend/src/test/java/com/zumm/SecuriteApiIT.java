package com.zumm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
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
 * <p>La matrice RBAC par role (US-005, US-022) sera testee au SPRINT-01, une fois
 * les profils arretes.
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
    @WithMockUser
    @DisplayName("laisse passer un appelant authentifie sur un endpoint protege")
    void laissePasserUnAppelantAuthentifie() throws Exception {
        // Authentifie, la requete depasse la securite : 404 attendu puisque
        // l'endpoint n'existe pas encore.
        mockMvc.perform(get("/api/ruchers"))
                .andExpect(status().isNotFound());
    }
}
