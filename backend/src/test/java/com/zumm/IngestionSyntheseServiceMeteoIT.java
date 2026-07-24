package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
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
 * Verifie le SPRINT-06 contre un PostgreSQL reel : ingestion de mesures (US-017),
 * alertes a hysteresis (US-018), synthese ROI (US-015), service tierce
 * getZummHoneyActualQuantity + OpenAPI (US-026) et contexte meteo (US-029, forcé en
 * simulation pour rester hors-ligne).
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs",
        "zumm.meteo.mode=simulation"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IngestionSyntheseServiceMeteoIT {

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
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_responsable"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_superviseur"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    @Test
    @DisplayName("US-017/018 : ingestion, alerte poids ouverte puis fermée (hystérésis)")
    void ingestionEtAlerteHysteresis() throws Exception {
        String t = "sp06-ingest";
        long rucheId = chaineRuche(t);

        // Poids 10 kg < seuil 15 kg → alerte critique ouverte.
        String rep = ingerer(t, rucheId, "poids", "10.0", "2026-06-01T08:00:00Z");
        Assertions.assertEquals(1, json.readTree(rep).get("alertes").size());
        Assertions.assertEquals("critique", json.readTree(rep).get("alertes").get(0).get("niveau").asText());

        // Toujours sous le seuil → alerte déjà ouverte, aucun nouveau déclenchement.
        String rep2 = ingerer(t, rucheId, "poids", "12.0", "2026-06-01T09:00:00Z");
        Assertions.assertEquals(0, json.readTree(rep2).get("alertes").size());

        // 16 kg ≥ 15 + marge (0,75) → retour zone sûre → alerte fermée.
        String rep3 = ingerer(t, rucheId, "poids", "16.0", "2026-06-01T10:00:00Z");
        Assertions.assertEquals(1, json.readTree(rep3).get("alertes").size());
        Assertions.assertFalse(json.readTree(rep3).get("alertes").get(0).get("ouverte").asBoolean());

        // Plus aucune alerte ouverte.
        mockMvc.perform(get("/api/mesures/alertes").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // US-013 (cross-sprint) : la production reflète le dernier poids ingéré.
        mockMvc.perform(get("/api/tableaux/production").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].poidsActuelKg").value(16.0))
                .andExpect(jsonPath("$[0].nombreMesures").value(3));
    }

    @Test
    @DisplayName("US-026 : getZummHoneyActualQuantity convertit kg → g ; OpenAPI publié")
    void serviceTierceEtOpenApi() throws Exception {
        String t = "sp06-miel";
        long rucheId = chaineRuche(t);
        ingerer(t, rucheId, "poids", "16.0", "2026-06-02T10:00:00Z");

        String rep = mockMvc.perform(get("/api/services/getZummHoneyActualQuantity").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)).param("unite", "g"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unite").value("g"))
                .andReturn().getResponse().getContentAsString();
        Assertions.assertEquals(16000.0, json.readTree(rep).get("quantite").asDouble(), 0.001);

        // Contrat OpenAPI 3 public, exposant l'opération.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/services/getZummHoneyActualQuantity']").exists());
    }

    @Test
    @DisplayName("US-015 : synthèse ROI agrège ruches, visites et production")
    void syntheseRoi() throws Exception {
        String t = "sp06-synthese";
        long rucheId = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Ana\",\"role\":\"apiculteur\"}");
        idApres(t, "/api/visites",
                ("{\"rucheId\":%d,\"agentId\":%d,\"dateVisite\":\"2026-06-03\",\"raison\":\"recolte\","
                        + "\"productivite\":3}").formatted(rucheId, agentId));
        ingerer(t, rucheId, "poids", "20.0", "2026-06-03T10:00:00Z");

        mockMvc.perform(get("/api/tableaux/synthese").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreRuches").value(1))
                .andExpect(jsonPath("$.nombreVisites").value(1))
                .andExpect(jsonPath("$.visitesParRaison.recolte").value(1))
                .andExpect(jsonPath("$.poidsTotalActuelKg").value(20.0))
                .andExpect(jsonPath("$.roi.valeurProductionEur").value(240.0))
                .andExpect(jsonPath("$.roi.coutInterventionsEur").value(25.0));
    }

    @Test
    @DisplayName("US-029 : contexte météo d'un site (simulation hors-ligne)")
    void meteoSite() throws Exception {
        String t = "sp06-meteo";
        long fermierId = idApres(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        long fermeId = idApres(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
        long siteId = idApres(t, "/api/sites",
                "{\"nom\":\"S\",\"fermeId\":" + fermeId
                        + ",\"latitude\":36.8,\"longitude\":10.2,\"dateMiseEnOeuvre\":\"2026-04-01\"}");

        mockMvc.perform(get("/api/meteo").with(tenant(t)).param("siteId", String.valueOf(siteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteId").value((int) siteId))
                .andExpect(jsonPath("$.source").value("simulation"))
                .andExpect(jsonPath("$.temperatureCelsius").exists());
    }

    @Test
    @DisplayName("isole les mesures et alertes entre tenants")
    void isoleLesTenants() throws Exception {
        String t = "sp06-prop";
        long rucheId = chaineRuche(t);
        ingerer(t, rucheId, "poids", "9.0", "2026-06-04T10:00:00Z");
        mockMvc.perform(get("/api/mesures/alertes").with(tenant("sp06-autre")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String ingerer(String t, long rucheId, String type, String valeur, String instant)
            throws Exception {
        return mockMvc.perform(post("/api/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"rucheId\":%d,\"typeIndicateur\":\"%s\",\"valeur\":%s,\"instant\":\"%s\"}")
                                .formatted(rucheId, type, valeur, instant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

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
