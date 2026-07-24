package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifie de bout en bout le CRUD Site (US-003, avec proximite PostGIS), le CRUD
 * Agent (US-005), les contraintes de composition (US-006) et l'exposition des
 * seuils de {@code ConfigZumm.ini} (US-025), contre un PostgreSQL reel.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrudSiteAgentConfigIT {

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

    // ─── US-003 : Site + géolocalisation PostGIS ───

    @Test
    @DisplayName("cree un site geolocalise et le retrouve par proximite (PostGIS)")
    void creeSiteEtRechercheProximite() throws Exception {
        String t = "site-a";
        long fermeId = creerFermeAvecFermier(t);

        // Site a Cahors (~44.447, 1.441).
        String corps = """
                {"nom":"Rucher du Lot","fermeId":%d,"latitude":44.447,"longitude":1.441,
                 "altitude":135.0,"dateMiseEnOeuvre":"2026-04-01"}""".formatted(fermeId);
        mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Rucher du Lot"))
                .andExpect(jsonPath("$.fermeNom").exists());

        // A ~2 km : trouve. A 500 m d'un point eloigne : absent.
        mockMvc.perform(get("/api/sites/proches").with(tenant(t))
                        .param("latitude", "44.45").param("longitude", "1.45")
                        .param("rayonMetres", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("Rucher du Lot"));
        mockMvc.perform(get("/api/sites/proches").with(tenant(t))
                        .param("latitude", "48.85").param("longitude", "2.35")
                        .param("rayonMetres", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── US-006 : contraintes de composition ───

    @Test
    @DisplayName("rejette un site dont la cloture precede la mise en oeuvre (US-006)")
    void rejetteDatesIncoherentes() throws Exception {
        String t = "site-b";
        long fermeId = creerFermeAvecFermier(t);
        String corps = """
                {"nom":"Site incoherent","fermeId":%d,"latitude":45.0,"longitude":1.0,
                 "dateMiseEnOeuvre":"2026-05-01","dateCloture":"2026-04-01"}""".formatted(fermeId);
        mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("rejette une latitude hors bornes (US-006)")
    void rejetteLatitudeHorsBornes() throws Exception {
        String t = "site-c";
        long fermeId = creerFermeAvecFermier(t);
        String corps = """
                {"nom":"Site improbable","fermeId":%d,"latitude":120.0,"longitude":1.0,
                 "dateMiseEnOeuvre":"2026-05-01"}""".formatted(fermeId);
        mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
    }

    // ─── US-005 : Agent + rôles ───

    @Test
    @DisplayName("cree un agent avec un role et le relit")
    void creeAgentAvecRole() throws Exception {
        String t = "agent-a";
        String corps = "{\"nom\":\"Awa\",\"role\":\"superviseur\"}";
        String reponse = mockMvc.perform(post("/api/agents").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("superviseur"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(reponse).get("id").asLong();

        mockMvc.perform(get("/api/agents/" + id).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Awa"))
                .andExpect(jsonPath("$.role").value("superviseur"));
    }

    @Test
    @DisplayName("rejette un role d'agent inconnu")
    void rejetteRoleInconnu() throws Exception {
        mockMvc.perform(post("/api/agents").with(tenant("agent-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Zed\",\"role\":\"sorcier\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("isole les agents entre tenants")
    void isoleAgentsEntreTenants() throws Exception {
        String reponse = mockMvc.perform(post("/api/agents").with(tenant("agent-prop"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Prive\",\"role\":\"apiculteur\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(reponse).get("id").asLong();

        mockMvc.perform(get("/api/agents/" + id).with(tenant("agent-autre")))
                .andExpect(status().isNotFound());
    }

    // ─── US-025 : seuils de ConfigZumm.ini ───

    @Test
    @DisplayName("expose les seuils par defaut de ConfigZumm.ini")
    void exposeLesSeuils() throws Exception {
        mockMvc.perform(get("/api/configuration/seuils").with(tenant("cfg")))
                .andExpect(status().isOk())
                // Defauts alignes sur le gabarit config/ConfigZumm.example.ini.
                .andExpect(jsonPath("$.poidsRucheAlerteKg").value(15))
                .andExpect(jsonPath("$.delaiAlerteJours").value(21))
                .andExpect(jsonPath("$.languesActives[0]").value("fr"));
    }

    /** Cree un fermier puis une ferme sous un tenant, renvoie l'identifiant de ferme. */
    private long creerFermeAvecFermier(String tenantId) throws Exception {
        String rf = mockMvc.perform(post("/api/fermiers").with(tenant(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Fermier " + tenantId + "\",\"contact\":null}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long fermierId = json.readTree(rf).get("id").asLong();

        String rfe = mockMvc.perform(post("/api/fermes").with(tenant(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ferme " + tenantId + "\",\"fermierId\":" + fermierId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rfe).get("id").asLong();
    }
}
