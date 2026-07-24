package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.startsWith;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Verifie le SPRINT-07 contre un PostgreSQL reel : suivi de la reine (US-032),
 * recolte + QR/tracabilite (US-033) et detection d'anomalie EWMA (US-034).
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReineRecolteAnomalieIT {

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
    @DisplayName("US-032 : événements de reine, historique et statut invalide rejeté")
    void suiviReine() throws Exception {
        String t = "sp07-reine";
        long rucheId = chaineRuche(t);

        idApres(t, "/api/reines",
                ("{\"rucheId\":%d,\"dateEvenement\":\"2026-05-01\",\"statut\":\"introduite\","
                        + "\"couleurMarquage\":\"vert\",\"anneeNaissance\":2024,\"race\":\"Buckfast\"}")
                        .formatted(rucheId));
        idApres(t, "/api/reines",
                ("{\"rucheId\":%d,\"dateEvenement\":\"2026-06-15\",\"statut\":\"en_ponte\"}")
                        .formatted(rucheId));

        // Historique : le plus récent d'abord.
        mockMvc.perform(get("/api/reines").with(tenant(t)).param("rucheId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].statut").value("en_ponte"))
                .andExpect(jsonPath("$[1].couleurMarquage").value("vert"));

        // Statut hors liste → 400 (Bean Validation).
        mockMvc.perform(post("/api/reines").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"rucheId\":%d,\"dateEvenement\":\"2026-06-16\",\"statut\":\"volée\"}")
                                .formatted(rucheId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("US-033 : récolte génère un lot, QR payload et fiche de traçabilité")
    void recolteEtTracabilite() throws Exception {
        String t = "sp07-recolte";
        long rucheId = chaineRuche(t);

        String rep = mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"rucheId\":%d,\"dateRecolte\":\"2026-07-15\",\"quantiteKg\":18.500,"
                                + "\"typeMiel\":\"Toutes fleurs\"}").formatted(rucheId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lot").value(startsWith("ZUMM-")))
                .andExpect(jsonPath("$.qrPayload").value(startsWith("zumm:tracabilite:ZUMM-")))
                .andReturn().getResponse().getContentAsString();
        String lot = json.readTree(rep).get("lot").asText();

        // Fiche de traçabilité scannée depuis le QR (remonte site et ferme).
        mockMvc.perform(get("/api/recoltes/tracabilite/" + lot).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lot").value(lot))
                .andExpect(jsonPath("$.rucheModele").value("M"))
                .andExpect(jsonPath("$.siteNom").value("S"))
                .andExpect(jsonPath("$.fermeNom").value("Fe"))
                .andExpect(jsonPath("$.quantiteKg").value(18.5));

        // Lot inconnu → 404.
        mockMvc.perform(get("/api/recoltes/tracabilite/ZUMM-0-00000000-99").with(tenant(t)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("US-034 : EWMA repère une pointe de poids comme anomalie")
    void detectionAnomalieEwma() throws Exception {
        String t = "sp07-anomalie";
        long rucheId = chaineRuche(t);

        // Série légèrement bruitée autour de 30 kg, puis une pointe à 50 kg.
        double[] valeurs = {30.0, 30.2, 29.9, 30.1, 29.8, 30.3, 29.9, 50.0};
        for (int i = 0; i < valeurs.length; i++) {
            ingerer(t, rucheId, valeurs[i], "2026-06-%02dT10:00:00Z".formatted(i + 1));
        }

        String rep = mockMvc.perform(get("/api/anomalies").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)).param("type", "poids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombrePoints").value(8))
                .andExpect(jsonPath("$.baseline").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode anomalies = json.readTree(rep).get("anomalies");
        boolean pointeReperee = false;
        for (JsonNode a : anomalies) {
            if (a.get("valeur").asDouble() == 50.0) {
                pointeReperee = true;
            }
        }
        Assertions.assertTrue(pointeReperee, "La pointe à 50 kg doit être signalée comme anomalie");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void ingerer(String t, long rucheId, double valeur, String instant) throws Exception {
        mockMvc.perform(post("/api/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"rucheId\":%d,\"typeIndicateur\":\"poids\",\"valeur\":%s,\"instant\":\"%s\"}")
                                .formatted(rucheId, valeur, instant)))
                .andExpect(status().isCreated());
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
