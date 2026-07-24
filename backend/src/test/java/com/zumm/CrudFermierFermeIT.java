package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Verifie de bout en bout le CRUD Fermier (US-001) et Ferme (US-002) a travers
 * l'API, contre un PostgreSQL reel, en incluant l'isolation par tenant : le
 * tenant est porte par le claim {@code tenant_id} du jeton.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CrudFermierFermeIT {

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

    /** Jeton d'un tenant donne : seul le claim tenant_id compte pour l'isolation. */
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
    @DisplayName("refuse l'acces sans jeton")
    void refuseSansJeton() throws Exception {
        mockMvc.perform(get("/api/fermiers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cree un fermier puis le relit")
    void creeEtRelitUnFermier() throws Exception {
        long id = creerFermier("alpha", "Rucher Alpha", "alpha@example.org");

        mockMvc.perform(get("/api/fermiers/" + id).with(tenant("alpha")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Rucher Alpha"))
                .andExpect(jsonPath("$.contact").value("alpha@example.org"));
    }

    @Test
    @DisplayName("isole les fermiers entre tenants a travers l'API")
    void isoleLesTenants() throws Exception {
        long id = creerFermier("alpha", "Rucher prive", null);

        // Un autre tenant ne voit ni la ressource par id, ni dans sa liste.
        mockMvc.perform(get("/api/fermiers/" + id).with(tenant("beta")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/fermiers").with(tenant("beta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }

    @Test
    @DisplayName("rejette un fermier sans nom (validation)")
    void rejetteFermierSansNom() throws Exception {
        mockMvc.perform(post("/api/fermiers").with(tenant("alpha"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"  \",\"contact\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.champs.nom").exists());
    }

    @Test
    @DisplayName("cree une ferme rattachee a un fermier du meme tenant")
    void creeUneFermeRattachee() throws Exception {
        long fermierId = creerFermier("gamma", "Fermier Gamma", null);

        mockMvc.perform(post("/api/fermes").with(tenant("gamma"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ferme du plateau\",\"fermierId\":" + fermierId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nom").value("Ferme du plateau"))
                .andExpect(jsonPath("$.fermierId").value((int) fermierId))
                .andExpect(jsonPath("$.fermierNom").value("Fermier Gamma"));
    }

    @Test
    @DisplayName("refuse une ferme rattachee a un fermier inconnu dans le tenant")
    void refuseFermeAvecFermierInconnu() throws Exception {
        // Fermier cree sous « delta » mais reference depuis « epsilon » : invisible,
        // donc refuse (400), pas de fuite d'existence.
        long fermierId = creerFermier("delta", "Fermier Delta", null);

        mockMvc.perform(post("/api/fermes").with(tenant("epsilon"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ferme pirate\",\"fermierId\":" + fermierId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("met a jour puis supprime un fermier")
    void metAJourEtSupprime() throws Exception {
        long id = creerFermier("zeta", "Ancien nom", null);

        mockMvc.perform(put("/api/fermiers/" + id).with(tenant("zeta"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Nouveau nom\",\"contact\":\"zeta@example.org\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Nouveau nom"));

        mockMvc.perform(delete("/api/fermiers/" + id).with(tenant("zeta")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/fermiers/" + id).with(tenant("zeta")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("refuse de supprimer un fermier encore rattache a une ferme (409)")
    void refuseSuppressionAvecFerme() throws Exception {
        long fermierId = creerFermier("eta", "Fermier Eta", null);
        mockMvc.perform(post("/api/fermes").with(tenant("eta"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Ferme liee\",\"fermierId\":" + fermierId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/fermiers/" + fermierId).with(tenant("eta")))
                .andExpect(status().isConflict());
    }

    /** Cree un fermier via l'API sous un tenant, renvoie son identifiant. */
    private long creerFermier(String tenantId, String nom, String contact) throws Exception {
        String corps = json.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("nom", nom);
            put("contact", contact);
        }});
        String reponse = mockMvc.perform(post("/api/fermiers").with(tenant(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode noeud = json.readTree(reponse);
        return noeud.get("id").asLong();
    }
}
