package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifie le workflow de visite de bout en bout (SPRINT-03) : planifier (US-007),
 * approuver/refuser (US-008), realiser + rapport (US-009), photos (US-010/028), et
 * l'isolation inter-tenant, contre un PostgreSQL reel.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WorkflowVisiteIT {

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

    private JwtRequestPostProcessor tenant(String tenantId) {
        // jwt() court-circuite le convertisseur applicatif : on fixe les autorites
        // directement. Ces tests s'executent avec tous les roles (acces complet).
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_responsable"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_superviseur"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    @Test
    @DisplayName("planifie, approuve, réalise le rapport et ajoute une photo")
    void workflowComplet() throws Exception {
        String t = "visite-a";
        long rucheId = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Ava\",\"role\":\"apiculteur\"}");

        // US-007 : planifier.
        long planningId = idApres(t, "/api/plannings",
                "{\"rucheId\":%d,\"agentId\":%d,\"datePrevue\":\"2026-09-01\",\"raison\":\"controle\"}"
                        .formatted(rucheId, agentId));
        mockMvc.perform(get("/api/plannings/" + planningId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("propose"));

        // US-008 : approuver.
        mockMvc.perform(post("/api/plannings/" + planningId + "/approuver").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("approuve"));

        // US-009 : réaliser la visite avec rapport, liée au planning.
        long visiteId = idApres(t, "/api/visites",
                ("{\"rucheId\":%d,\"agentId\":%d,\"planningId\":%d,\"dateVisite\":\"2026-09-02\","
                        + "\"raison\":\"controle\",\"constatations\":\"Colonie vigoureuse\","
                        + "\"etatSante\":\"bon\",\"effectifQualitatif\":\"fort\",\"productivite\":3}")
                        .formatted(rucheId, agentId, planningId));
        mockMvc.perform(get("/api/visites/" + visiteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constatations").value("Colonie vigoureuse"))
                .andExpect(jsonPath("$.etatSante").value("bon"))
                .andExpect(jsonPath("$.productivite").value(3))
                .andExpect(jsonPath("$.planningId").value((int) planningId));

        // US-010/028 : ajouter une photo puis la lister.
        mockMvc.perform(post("/api/visites/" + visiteId + "/photos").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://stockage.zumm/visite/1.jpg\",\"legende\":\"Cadre de couvain\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/visites/" + visiteId + "/photos").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].legende").value("Cadre de couvain"));
    }

    @Test
    @DisplayName("refuse un planning : motif obligatoire, statut refusé")
    void refusMotive() throws Exception {
        String t = "visite-b";
        long rucheId = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Ben\",\"role\":\"superviseur\"}");
        long planningId = idApres(t, "/api/plannings",
                "{\"rucheId\":%d,\"agentId\":%d,\"datePrevue\":\"2026-09-01\"}".formatted(rucheId, agentId));

        // Refus sans motif → 400.
        mockMvc.perform(post("/api/plannings/" + planningId + "/refuser").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // Refus motivé → 200, statut refusé.
        mockMvc.perform(post("/api/plannings/" + planningId + "/refuser").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motif\":\"Météo défavorable\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("refuse"))
                .andExpect(jsonPath("$.motifRefus").value("Météo défavorable"));
    }

    @Test
    @DisplayName("isole les plannings entre tenants")
    void isoleLesTenants() throws Exception {
        String t = "visite-prop";
        long rucheId = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Cid\",\"role\":\"apiculteur\"}");
        long planningId = idApres(t, "/api/plannings",
                "{\"rucheId\":%d,\"agentId\":%d,\"datePrevue\":\"2026-09-01\"}".formatted(rucheId, agentId));

        mockMvc.perform(get("/api/plannings/" + planningId).with(tenant("visite-autre")))
                .andExpect(status().isNotFound());
    }

    // ─── Chaine fermier -> ferme -> site -> ruche ─────────────────────────────

    private long chaineRuche(String t) throws Exception {
        long fermierId = idApres(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        long fermeId = idApres(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
        long siteId = idApres(t, "/api/sites",
                "{\"nom\":\"S\",\"fermeId\":" + fermeId
                        + ",\"latitude\":45.0,\"longitude\":1.0,\"dateMiseEnOeuvre\":\"2026-04-01\"}");
        return idApres(t, "/api/ruches",
                "{\"modele\":\"M\",\"siteId\":" + siteId + ",\"fermeId\":" + fermeId
                        + ",\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}");
    }

    private long idApres(String t, String url, String corps) throws Exception {
        MockHttpServletRequestBuilder requete = post(url).with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON).content(corps);
        String rep = mockMvc.perform(requete)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("id").asLong();
    }
}
