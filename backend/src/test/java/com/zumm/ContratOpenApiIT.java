package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Publie le contrat OpenAPI 3 sous forme de FICHIER, pour que le client TypeScript
 * puisse en deriver ses types (US-026).
 *
 * <p>Pourquoi un test et non un greffon Maven : les greffons de generation OpenAPI
 * demarrent l'application entiere pour interroger {@code /v3/api-docs}, donc
 * exigent une base. Ce projet en a deja une, orchestree par Testcontainers, dans
 * la campagne d'integration. Reutiliser ce harnais evite un second mecanisme de
 * demarrage a maintenir.
 *
 * <p>Le fichier produit est VERSIONNE, comme le sont les PDF du cahier des
 * charges : le depot suit la meme regle depuis le SPRINT-00 — un artefact derive
 * qui sert de reference est committe, et la CI verifie qu'il correspond bien a ses
 * sources.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ContratOpenApiIT {

    /** Emplacement du contrat, cote front : c'est lui qui le consomme. */
    private static final Path CONTRAT = Path.of("..", "frontend", "src", "api", "openapi.json");

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

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("publie le contrat OpenAPI et en verifie la substance")
    void publieLeContrat() throws Exception {
        String brut = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var arbre = json.readTree(brut);

        // Garde-fous : un contrat vide se publierait sans erreur et casserait la
        // generation de types cote front sans que rien ne le signale.
        assertThat(arbre.path("openapi").asText()).startsWith("3.");
        assertThat(arbre.path("paths").size())
                .as("le contrat doit decrire les endpoints de l'API")
                .isGreaterThan(20);
        assertThat(arbre.path("components").path("schemas").size())
                .as("le contrat doit decrire les schemas de donnees")
                .isGreaterThan(20);

        // Ecriture indentee et stable : un contrat reformate a chaque execution
        // produirait un diff a chaque construction, et le controle de fraicheur
        // deviendrait du bruit.
        Files.createDirectories(CONTRAT.getParent());
        Files.writeString(CONTRAT,
                json.writerWithDefaultPrettyPrinter().writeValueAsString(arbre) + "\n",
                StandardCharsets.UTF_8);
    }
}
