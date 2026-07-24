package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;

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
 * Verifie le SPRINT-05 de bout en bout contre un PostgreSQL reel : taches et
 * rappels (US-031), calendrier matriciel (US-012), tableau production (US-013),
 * alertes sanitaires (US-014) et export CSV/TXT (US-027).
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TableauxTachesExportIT {

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
    @DisplayName("US-031 : tâche, rappel échu, puis marquée faite")
    void tachesEtRappels() throws Exception {
        String t = "sp05-taches";
        long rucheId = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Ava\",\"role\":\"apiculteur\"}");

        // Une tâche échue et non faite → apparaît dans les rappels.
        long tacheId = idApres(t, "/api/taches",
                ("{\"libelle\":\"Vérifier réserves\",\"rucheId\":%d,\"agentId\":%d,"
                        + "\"echeance\":\"2026-01-15\",\"faite\":false}").formatted(rucheId, agentId));
        mockMvc.perform(get("/api/taches/rappels").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].libelle").value("Vérifier réserves"))
                .andExpect(jsonPath("$[0].rucheModele").value("M"));

        // Marquée faite → disparaît des rappels.
        mockMvc.perform(put("/api/taches/" + tacheId).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Vérifier réserves\",\"echeance\":\"2026-01-15\",\"faite\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faite").value(true));
        mockMvc.perform(get("/api/taches/rappels").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("US-012 : calendrier matriciel regroupe les visites agent × ruche")
    void calendrierMatrice() throws Exception {
        String t = "sp05-cal";
        long rucheId = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Ben\",\"role\":\"apiculteur\"}");
        creerVisite(t, rucheId, agentId, "2026-09-05", "bon");
        creerVisite(t, rucheId, agentId, "2026-09-20", "bon");
        // Hors période → ne doit pas compter.
        creerVisite(t, rucheId, agentId, "2026-10-10", "bon");

        mockMvc.perform(get("/api/tableaux/calendrier").with(tenant(t))
                        .param("debut", "2026-09-01").param("fin", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].agentId").value((int) agentId))
                .andExpect(jsonPath("$[0].rucheId").value((int) rucheId))
                .andExpect(jsonPath("$[0].nombreVisites").value(2));
    }

    @Test
    @DisplayName("US-014 : ruche jamais visitée = critique ; état mauvais = critique")
    void alertesSanitaires() throws Exception {
        String t = "sp05-sante";
        long rucheJamais = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Cid\",\"role\":\"apiculteur\"}");
        long rucheMauvaise = idApres(t, "/api/ruches",
                "{\"modele\":\"MAL\",\"siteId\":" + siteDe(t, rucheJamais) + ",\"fermeId\":"
                        + fermeDe(t, rucheJamais) + ",\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}");
        creerVisite(t, rucheMauvaise, agentId, "2026-06-01", "mauvais");

        String rep = mockMvc.perform(get("/api/tableaux/alertes-sanitaires").with(tenant(t)))
                .andExpect(status().isOk())
                // Critiques en tête.
                .andExpect(jsonPath("$[0].niveau").value("critique"))
                .andReturn().getResponse().getContentAsString();
        // Les deux ruches sont critiques (jamais visitée + état mauvais).
        org.junit.jupiter.api.Assertions.assertEquals(2, json.readTree(rep).size());
    }

    @Test
    @DisplayName("US-013 / US-027 : production listée et export CSV téléchargeable")
    void productionEtExport() throws Exception {
        String t = "sp05-prod";
        long rucheId = chaineRuche(t);
        long agentId = idApres(t, "/api/agents", "{\"nom\":\"Dan\",\"role\":\"apiculteur\"}");
        creerVisite(t, rucheId, agentId, "2026-09-10", "bon");

        // Production : une ligne par ruche (poids nul faute de mesures ce sprint).
        mockMvc.perform(get("/api/tableaux/production").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rucheModele").value("M"))
                .andExpect(jsonPath("$[0].productiviteMoyenne").value(2.0));

        // Export CSV des visites : en-tête, ligne, et téléchargement.
        mockMvc.perform(get("/api/export/visites").with(tenant(t)).param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("zumm-visites.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(startsWith("id,date,heure")))
                .andExpect(content().string(containsString(",bon,")));

        // Export TXT des ruches : séparateur tabulation.
        mockMvc.perform(get("/api/export/ruches").with(tenant(t)).param("format", "txt"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("zumm-ruches.txt")))
                .andExpect(content().string(startsWith("id\tmodele")));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void creerVisite(String t, long rucheId, long agentId, String date, String etatSante)
            throws Exception {
        idApres(t, "/api/visites",
                ("{\"rucheId\":%d,\"agentId\":%d,\"dateVisite\":\"%s\",\"raison\":\"controle\","
                        + "\"etatSante\":\"%s\",\"productivite\":2}")
                        .formatted(rucheId, agentId, date, etatSante));
    }

    private long siteDe(String t, long rucheId) throws Exception {
        return majLong(t, "/api/ruches/" + rucheId, "siteId");
    }

    private long fermeDe(String t, long rucheId) throws Exception {
        return majLong(t, "/api/ruches/" + rucheId, "fermeId");
    }

    private long majLong(String t, String url, String champ) throws Exception {
        String rep = mockMvc.perform(get(url).with(tenant(t)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get(champ).asLong();
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
