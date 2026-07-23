package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import javax.sql.DataSource;
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
 * Verifie le CRUD Ruche avec composition (US-004), le modele Mesure et son
 * hypertable TimescaleDB (US-016), et l'isolation inter-tenant sur ces nouvelles
 * entites (US-037), contre un PostgreSQL reel.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrudRucheMesureIT {

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

    @Autowired
    private MesureRepository mesures;

    @Autowired
    private DataSource dataSource;

    private JwtRequestPostProcessor tenant(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId));
    }

    // ─── US-004 : composition ────────────────────────────────────────────────

    @Test
    @DisplayName("cree une ruche avec un corps et deux hausses")
    void creeRucheComposee() throws Exception {
        String t = "ruche-a";
        long siteId = chaineSite(t);
        long fermeId = fermeDe(t, siteId);

        String corps = """
                {"modele":"Dadant 10","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10},
                                  {"type":"hausse","nbCadres":9},
                                  {"type":"hausse","nbCadres":9}]}""".formatted(siteId, fermeId);
        mockMvc.perform(post("/api/ruches").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("creee"))
                .andExpect(jsonPath("$.nbHausses").value(2))
                .andExpect(jsonPath("$.compartiments.length()").value(3));
    }

    @Test
    @DisplayName("refuse une ruche sans corps")
    void refuseSansCorps() throws Exception {
        String t = "ruche-b";
        long siteId = chaineSite(t);
        long fermeId = fermeDe(t, siteId);
        String corps = """
                {"modele":"X","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"hausse","nbCadres":9}]}""".formatted(siteId, fermeId);
        mockMvc.perform(post("/api/ruches").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuse une ruche avec plus de cinq hausses")
    void refuseTropDeHausses() throws Exception {
        String t = "ruche-c";
        long siteId = chaineSite(t);
        long fermeId = fermeDe(t, siteId);
        StringBuilder comp = new StringBuilder("{\"type\":\"corps\",\"nbCadres\":10}");
        for (int i = 0; i < 6; i++) {
            comp.append(",{\"type\":\"hausse\",\"nbCadres\":9}");
        }
        String corps = "{\"modele\":\"X\",\"siteId\":%d,\"fermeId\":%d,\"compartiments\":[%s]}"
                .formatted(siteId, fermeId, comp);
        mockMvc.perform(post("/api/ruches").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuse un compartiment de plus de dix cadres")
    void refuseTropDeCadres() throws Exception {
        String t = "ruche-d";
        long siteId = chaineSite(t);
        long fermeId = fermeDe(t, siteId);
        String corps = """
                {"modele":"X","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":11}]}""".formatted(siteId, fermeId);
        mockMvc.perform(post("/api/ruches").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("isole les ruches entre tenants (US-037)")
    void isoleRuchesEntreTenants() throws Exception {
        String t = "ruche-prop";
        long siteId = chaineSite(t);
        long fermeId = fermeDe(t, siteId);
        String corps = """
                {"modele":"Privée","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10}]}""".formatted(siteId, fermeId);
        String rep = mockMvc.perform(post("/api/ruches").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(rep).get("id").asLong();

        mockMvc.perform(get("/api/ruches/" + id).with(tenant("ruche-autre")))
                .andExpect(status().isNotFound());
    }

    // ─── US-016 : modele Mesure + hypertable ─────────────────────────────────

    @Test
    @DisplayName("persiste une mesure et la relit (modele hypertable)")
    void persisteUneMesure() throws Exception {
        String t = "mesure-a";
        long siteId = chaineSite(t);
        long fermeId = fermeDe(t, siteId);
        long rucheId = rucheDe(t, siteId, fermeId);

        MesureId id = new MesureId(rucheId, TypeIndicateur.POIDS, Instant.parse("2026-08-15T10:00:00Z"));
        TenantContext.executer(t, () -> mesures.save(new Mesure(id, new BigDecimal("42.5"))));

        var relue = TenantContext.executer(t, () -> mesures.findById(id));
        assertThat(relue).isPresent();
        assertThat(relue.get().getValeur()).isEqualByComparingTo("42.5");
        assertThat(relue.get().getTenantId()).isEqualTo(t);
    }

    @Test
    @DisplayName("declare la table mesure comme hypertable TimescaleDB")
    void mesureEstUneHypertable() throws Exception {
        try (var connexion = dataSource.getConnection();
                var requete = connexion.prepareStatement(
                        "SELECT count(*) FROM timescaledb_information.hypertables "
                                + "WHERE hypertable_name = 'mesure'");
                var resultat = requete.executeQuery()) {
            resultat.next();
            assertThat(resultat.getLong(1)).as("mesure doit être une hypertable").isEqualTo(1);
        }
    }

    // ─── Chaine de dependances (fermier -> ferme -> site -> ruche) ────────────

    /** Cree fermier + ferme + site sous un tenant, renvoie l'identifiant du site. */
    private long chaineSite(String t) throws Exception {
        long fermierId = idApres(post("/api/fermiers").with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"F\",\"contact\":null}"));
        long fermeId = idApres(post("/api/fermes").with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}"));
        return idApres(post("/api/sites").with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nom\":\"S\",\"fermeId\":" + fermeId
                        + ",\"latitude\":45.0,\"longitude\":1.0,\"dateMiseEnOeuvre\":\"2026-04-01\"}"));
    }

    /** Renvoie la ferme du site precedemment cree (relit le site). */
    private long fermeDe(String t, long siteId) throws Exception {
        String rep = mockMvc.perform(get("/api/sites/" + siteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("fermeId").asLong();
    }

    private long rucheDe(String t, long siteId, long fermeId) throws Exception {
        return idApres(post("/api/ruches").with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modele\":\"M\",\"siteId\":" + siteId + ",\"fermeId\":" + fermeId
                        + ",\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}"));
    }

    private long idApres(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete)
            throws Exception {
        String rep = mockMvc.perform(requete)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("id").asLong();
    }
}
