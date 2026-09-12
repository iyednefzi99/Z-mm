package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Preuve de bout en bout (SPRINT-34) : des refus RBAC repetes du meme acteur
 * laissent une trace dans le journal d'audit ET engendrent une tache critique —
 * sous la vraie RLS PostgreSQL, pas un mock. Le seuil par defaut
 * ({@link com.zumm.configmetier.SeuilsMetier#defauts()}) est 5 refus en 15
 * minutes ; ce test n'en depend que pour le nombre de tentatives.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AnomalieAccesIT {

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

    private JwtRequestPostProcessor jeton(String tid, String sujet, String... roles) {
        var autorites = java.util.Arrays.stream(roles)
                .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                .toArray(org.springframework.security.core.GrantedAuthority[]::new);
        return jwt().jwt(b -> b.subject(sujet).claim("tenant_id", tid)).authorities(autorites);
    }

    @Test
    @DisplayName("5 refus du meme acteur : journalises, puis une tache critique engendree")
    void refusRepetesEngendrentUneAlerte() throws Exception {
        String t = "anomalie-a";
        String corpsFermier = "{\"nom\":\"Rucher\",\"contact\":null}";

        // Un apiculteur n'a pas le droit d'ecrire le referentiel (RbacIT le prouve
        // deja) : chaque tentative se heurte a GestionnaireRefusAcces.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/fermiers").with(jeton(t, "agent-suspect", "apiculteur"))
                            .contentType(MediaType.APPLICATION_JSON).content(corpsFermier))
                    .andExpect(status().isForbidden());
        }

        String audit = mockMvc.perform(get("/api/audit").with(jeton(t, "admin-a", "admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long refusDeAgentSuspect = StreamSupport.stream(json.readTree(audit).spliterator(), false)
                .filter(e -> "refus".equals(e.get("action").asText()))
                .filter(e -> "agent-suspect".equals(e.get("acteur").asText()))
                .count();
        assertThat(refusDeAgentSuspect).isEqualTo(5);

        String taches = mockMvc.perform(get("/api/taches").with(jeton(t, "admin-a", "admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        boolean tacheAnomalie = StreamSupport.stream(json.readTree(taches).spliterator(), false)
                .anyMatch(x -> "anomalie-acces".equals(texte(x, "regleCode"))
                        && "critique".equals(texte(x, "priorite"))
                        && "regle".equals(texte(x, "origine")));
        assertThat(tacheAnomalie).isTrue();
    }

    private static String texte(JsonNode noeud, String champ) {
        JsonNode valeur = noeud.get(champ);
        return valeur == null || valeur.isNull() ? null : valeur.asText();
    }
}
