package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Éprouve le SPRINT-25, lot J — identification, comptes et confort.
 *
 * <p>Le lot est fait de sept petites lignes sans lien technique entre elles.
 * Trois seulement touchent le serveur, et ce sont celles-ci :
 *
 * <ol>
 *   <li>la préférence de notification est <strong>par agent</strong>, et un
 *       corps qui l'omet ne la réécrit pas — sans cette garde, renommer un agent
 *       rallumerait des notifications coupées exprès ;
 *   <li>le jeu de démonstration est <strong>réversible</strong> : la purge
 *       retire ce que le chargement a créé, et <strong>rien d'autre</strong> ;
 *   <li>il est <strong>fermé</strong> — désactivé par défaut sur un
 *       déploiement, et réservé au rôle {@code admin}.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs",
        // La démonstration est ÉTEINTE par défaut : il faut l'allumer ici pour
        // l'éprouver, ce qui est en soi la vérification du verrou.
        "zumm.demonstration.activee=true",
        "zumm.auth.reinitialisation-url=https://identite.zumm.test/reset"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConfortIdentificationIT {

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
                        new SimpleGrantedAuthority("ROLE_admin"),
                        new SimpleGrantedAuthority("ROLE_responsable"),
                        new SimpleGrantedAuthority("ROLE_superviseur"),
                        new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    /** Profil de terrain : ni administrateur, ni responsable. */
    private JwtRequestPostProcessor apiculteur(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    // ─── Préférence de notification ──────────────────────────────────────────

    @Test
    @DisplayName("un agent naît avec ses notifications actives, et peut les couper")
    void preferenceDeNotification() throws Exception {
        String t = "sp25-notifications";
        long agentId = creer(t, "/api/agents",
                "{\"nom\":\"Amine\",\"role\":\"apiculteur\",\"email\":\"amine@zumm.test\"}");

        // Défaut à VRAI, délibérément : une alerte s'ouvre parce que quelque
        // chose ne va pas dans une ruche. Un défaut à faux ferait taire, au
        // premier déploiement, ce que le produit promet de signaler.
        mockMvc.perform(get("/api/agents/" + agentId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsEmail").value(true));

        mockMvc.perform(put("/api/agents/" + agentId).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Amine\",\"role\":\"apiculteur\","
                                + "\"email\":\"amine@zumm.test\",\"notificationsEmail\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationsEmail").value(false));
    }

    @Test
    @DisplayName("un corps qui omet la préférence ne la réécrit pas")
    void preferenceNonEcraseeParOmission() throws Exception {
        String t = "sp25-omission";
        long agentId = creer(t, "/api/agents",
                "{\"nom\":\"Sonia\",\"role\":\"apiculteur\",\"email\":\"sonia@zumm.test\","
                        + "\"notificationsEmail\":false}");

        // Renommer l'agent depuis un appelant qui ignore le champ ne doit PAS
        // rallumer des notifications coupées exprès. C'est le seul vrai piège de
        // cette ligne, et il ne se voit qu'en base.
        mockMvc.perform(put("/api/agents/" + agentId).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Sonia Haddad\",\"role\":\"apiculteur\","
                                + "\"email\":\"sonia@zumm.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Sonia Haddad"))
                .andExpect(jsonPath("$.notificationsEmail").value(false));
    }

    // ─── Jeu de démonstration ────────────────────────────────────────────────

    @Test
    @DisplayName("la démonstration se charge, puis se retire sans laisser de trace")
    void demonstrationReversible() throws Exception {
        String t = "sp25-demo";

        mockMvc.perform(get("/api/demonstration").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.charge").value(false));

        mockMvc.perform(post("/api/demonstration").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charge").value(true))
                // Un fermier, une ferme, un agent, deux ruchers, cinq ruches.
                .andExpect(jsonPath("$.objets").value(10));

        mockMvc.perform(get("/api/ruches").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(get("/api/visites").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(delete("/api/demonstration").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charge").value(false))
                .andExpect(jsonPath("$.objets").value(0));

        // Rien ne subsiste — y compris les visites, qui partent en cascade avec
        // leur ruche et ne sont donc PAS tracées : les tracer aurait produit une
        // purge qui supprime deux fois, et échoue la seconde.
        mockMvc.perform(get("/api/ruches").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/sites").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/visites").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("la purge ne touche PAS aux données saisies par l'utilisateur")
    void purgeNeToucheQueLaDemonstration() throws Exception {
        String t = "sp25-demo-coexistence";

        // Une exploitation réelle, dont un rucher porte le mot « démonstration »
        // dans son nom : c'est exactement le cas qu'une purge par nom
        // détruirait.
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"Vrai fermier\",\"contact\":null}");
        long fermeId = creer(t, "/api/fermes",
                "{\"nom\":\"Vraie ferme\",\"fermierId\":" + fermierId + "}");
        long siteId = creer(t, "/api/sites", ("""
                {"nom":"[démo] Rucher de démonstration","fermeId":%d,"latitude":44.5,
                 "longitude":1.5,"dateMiseEnOeuvre":"2026-04-01"}""").formatted(fermeId));
        creer(t, "/api/ruches", ("""
                {"modele":"[démo] Dadant","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10}]}""")
                .formatted(siteId, fermeId));

        mockMvc.perform(post("/api/demonstration").with(tenant(t))).andExpect(status().isOk());
        mockMvc.perform(get("/api/ruches").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(6));

        mockMvc.perform(delete("/api/demonstration").with(tenant(t))).andExpect(status().isOk());

        // La ruche de l'utilisateur survit, bien qu'elle porte la même marque :
        // la purge suit la TRACE, jamais les noms.
        mockMvc.perform(get("/api/ruches").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].modele").value("[démo] Dadant"));
        mockMvc.perform(get("/api/sites").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("un second chargement est refusé tant que le premier est en place")
    void chargementUnique() throws Exception {
        String t = "sp25-demo-double";
        mockMvc.perform(post("/api/demonstration").with(tenant(t))).andExpect(status().isOk());

        // Empiler deux jeux rendrait la trace ambiguë — et c'est la trace qui
        // rend la purge sûre.
        mockMvc.perform(post("/api/demonstration").with(tenant(t)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("la démonstration est fermée à tout rôle autre qu'administrateur")
    void demonstrationReserveeAdmin() throws Exception {
        String t = "sp25-demo-rbac";
        // Écrire vingt lignes dans l'exploitation d'autrui n'est pas un geste
        // d'apiculteur — et la LECTURE est fermée aussi : savoir si un jeu est
        // chargé n'intéresse que celui qui peut le retirer.
        mockMvc.perform(get("/api/demonstration").with(apiculteur(t)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/demonstration").with(apiculteur(t)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/demonstration").with(apiculteur(t)))
                .andExpect(status().isForbidden());
    }

    // ─── Mot de passe oublié ─────────────────────────────────────────────────

    @Test
    @DisplayName("l'URL de réinitialisation est servie par la route publique d'identité")
    void urlDeReinitialisation() throws Exception {
        // Route PUBLIQUE, et il le faut : celui qui a oublié son mot de passe
        // n'est, par construction, pas connecté. Ce n'est pas un secret — c'est
        // l'adresse d'une page de connexion.
        mockMvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reinitialisationUrl")
                        .value("https://identite.zumm.test/reset"));
    }

    private long creer(String t, String url, String corps) throws Exception {
        MockHttpServletRequestBuilder requete = post(url).with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON).content(corps);
        String rep = mockMvc.perform(requete)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("id").asLong();
    }
}
