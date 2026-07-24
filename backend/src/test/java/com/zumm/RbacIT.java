package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
 * Verifie la matrice RBAC (US-022) : chaque role n'a que les droits prevus par le
 * cahier. On teste les deux regles les plus structurantes — l'ecriture du
 * referentiel reservee au responsable/administrateur, et l'approbation d'un
 * planning reservee au superviseur.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RbacIT {

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

    private JwtRequestPostProcessor tenant(String tid, String... roles) {
        // jwt() court-circuite le convertisseur applicatif : on pose les autorites
        // « ROLE_<role> » directement, telles que les attend la matrice RBAC.
        var autorites = java.util.Arrays.stream(roles)
                .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                .toArray(org.springframework.security.core.GrantedAuthority[]::new);
        return jwt().jwt(b -> b.claim("tenant_id", tid)).authorities(autorites);
    }

    @Test
    @DisplayName("écriture du référentiel : refusée à l'apiculteur, permise à l'admin")
    void ecritureReferentiel() throws Exception {
        String t = "rbac-a";
        String corps = "{\"nom\":\"Rucher\",\"contact\":null}";

        mockMvc.perform(post("/api/fermiers").with(tenant(t, "apiculteur"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/fermiers").with(tenant(t, "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("approbation d'un planning : refusée à l'apiculteur, permise au superviseur")
    void approbationPlanning() throws Exception {
        String t = "rbac-b";
        // Mise en place avec un administrateur (droits sur le referentiel).
        long fermierId = idAdmin(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        long fermeId = idAdmin(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
        long siteId = idAdmin(t, "/api/sites", "{\"nom\":\"S\",\"fermeId\":" + fermeId
                + ",\"latitude\":45.0,\"longitude\":1.0,\"dateMiseEnOeuvre\":\"2026-04-01\"}");
        long rucheId = idAdmin(t, "/api/ruches", "{\"modele\":\"M\",\"siteId\":" + siteId + ",\"fermeId\":"
                + fermeId + ",\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}");
        long agentId = idAdmin(t, "/api/agents", "{\"nom\":\"A\",\"role\":\"apiculteur\"}");
        // La creation d'un planning est ouverte a tout role authentifie (apiculteur).
        long planningId = idAvec(tenant(t, "apiculteur"), "/api/plannings",
                "{\"rucheId\":" + rucheId + ",\"agentId\":" + agentId + ",\"datePrevue\":\"2026-09-01\"}");

        mockMvc.perform(post("/api/plannings/" + planningId + "/approuver").with(tenant(t, "apiculteur")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/plannings/" + planningId + "/approuver").with(tenant(t, "superviseur")))
                .andExpect(status().isOk());
    }

    private long idAdmin(String t, String url, String corps) throws Exception {
        return idAvec(tenant(t, "admin"), url, corps);
    }

    private long idAvec(JwtRequestPostProcessor jeton, String url, String corps) throws Exception {
        String rep = mockMvc.perform(post(url).with(jeton)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("id").asLong();
    }
}
