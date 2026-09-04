package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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
 * Verifie le lot A du plan de couverture contre un PostgreSQL reel : moteur de
 * regles, carence opposable et indices de colonie (SPRINT-22).
 *
 * <p>Ce lot ne cree presque aucune donnee — il tire des conclusions de colonnes
 * livrees au SPRINT-20. Ce qu'on eprouve ici est donc uniquement ce qui ne se
 * voit qu'a l'execution :
 *
 * <ol>
 *   <li>une regle engendre la tache attendue, et ne l'engendre <strong>pas deux
 *       fois</strong> — l'index unique partiel {@code uq_tache_declencheur} est
 *       le garde-fou, et il ne se teste qu'en base ;
 *   <li>la carence <strong>refuse</strong> la recolte en 409, puis la laisse
 *       passer avec un motif, qui atterrit au journal d'audit sous l'action
 *       {@code forcage} — laquelle n'existait pas avant la V22 ;
 *   <li>un indice sans observation ne vaut pas zero : il vaut « non evalue »,
 *       et l'API doit le dire par {@code composantes = 0}.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ReglesEtCarenceIT {

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

    // ─── Moteur de regles ────────────────────────────────────────────────────

    @Test
    @DisplayName("la fin de carence engendre une tache, et une seule")
    void regleEngendreUneSeuleFois() throws Exception {
        String t = "s22-regles";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // Traitement termine hier, carence de 2 jours : la fin tombe demain,
        // donc dans le preavis de trois jours de la regle.
        creer(t, "/api/traitements", ("""
                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                 "dateDebut":"%s","dateFin":"%s","delaiCarenceJours":2}""")
                .formatted(rucheId, agentId, LocalDate.now().minusDays(10),
                        LocalDate.now().minusDays(1)));

        mockMvc.perform(post("/api/regles/executer").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origine").value("regle"))
                .andExpect(jsonPath("$[0].regleCode").value("carence-retrait"))
                .andExpect(jsonPath("$[0].priorite").value("haute"))
                .andExpect(jsonPath("$[0].categorie").value("traitement"));

        // Second passage le meme jour : rien. Sans la cle de declenchement, la
        // liste se remplirait de doublons jusqu'a ne plus etre lue.
        mockMvc.perform(post("/api/regles/executer").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // La tache est bien dans la liste, une fois.
        mockMvc.perform(get("/api/taches").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("des reserves au plus bas engendrent une tache critique")
    void regleReservesBasses() throws Exception {
        String t = "s22-reserves";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"%s","raison":"controle",
                 "constatations":"Colonie faible.",
                 "observation":{"cadresMiel":1}}""")
                .formatted(rucheId, agentId, LocalDate.now().minusDays(2)));

        mockMvc.perform(post("/api/regles/executer").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].regleCode").value("reserves-basses"))
                // Une famine ne se planifie pas la semaine prochaine.
                .andExpect(jsonPath("$[0].priorite").value("critique"))
                .andExpect(jsonPath("$[0].categorie").value("nourrissement"));
    }

    // ─── Carence opposable ───────────────────────────────────────────────────

    @Test
    @DisplayName("la carence refuse la recolte en 409, puis la laisse passer avec un motif audite")
    void carenceOpposable() throws Exception {
        String t = "s22-carence";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        creer(t, "/api/traitements", ("""
                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                 "dateDebut":"%s","dateFin":"%s","delaiCarenceJours":30}""")
                .formatted(rucheId, agentId, LocalDate.now().minusDays(5), LocalDate.now()));

        // Prefixe JSON sans guillemet final : un bloc de texte qui se terminerait
        // par un guillemet en collerait quatre d'affilee, que le compilateur lit
        // comme une chaine non fermee.
        String corps = ("""
                {"rucheId":%d,"dateRecolte":"%s","quantiteKg":12.5""")
                .formatted(rucheId, LocalDate.now());

        // 409 et non 400 : la requete est valide, c'est l'etat qui s'y oppose.
        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps + "}"))
                .andExpect(status().isConflict())
                // Un refus qui ne dit pas jusqu'a quand se contourne.
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("carence")));

        // Forcer sans motif : refuse, et cette fois en 400 — il manque bien
        // quelque chose dans la requete.
        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps + ",\"forcerCarence\":true}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corps + ",\"forcerCarence\":true,\"motifForcage\":"
                                + "\"Hausse posee apres la fin du traitement.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carenceForcee").value(true));

        // La trace : l'aspect d'audit consigne deja la creation ; ce qu'il ne
        // sait pas dire, c'est qu'une regle a ete ecartee, et pourquoi.
        String audit = mockMvc.perform(get("/api/audit").with(tenant(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("forcage").contains("Hausse posee apres la fin");
    }

    @Test
    @DisplayName("sans carence, la recolte passe comme avant")
    void recolteOrdinaire() throws Exception {
        String t = "s22-sans-carence";
        long rucheId = chaineRuche(t);

        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"dateRecolte":"%s","quantiteKg":18.5}""")
                                .formatted(rucheId, LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.carenceForcee").value(false));
    }

    // ─── Indices de colonie ──────────────────────────────────────────────────

    @Test
    @DisplayName("une colonie sans observation n'est pas saine : elle est non evaluee")
    void indiceSansObservation() throws Exception {
        String t = "s22-indice-vide";
        long rucheId = chaineRuche(t);

        mockMvc.perform(get("/api/indices").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                // `composantes = 0` est le signal : l'ecran doit alors afficher
                // « non evalue », jamais une jauge sur du vide.
                .andExpect(jsonPath("$[0].composantes").value(0))
                .andExpect(jsonPath("$[0].sante").value(0));
    }

    @Test
    @DisplayName("des cellules royales d'essaimage font monter le risque, pas la sante")
    void indiceRisqueEssaimage() throws Exception {
        String t = "s22-indice-essaimage";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"%s","raison":"controle",
                 "constatations":"Barbe a l'entree, cellules sur les cadres de rive.",
                 "etatSante":"bon",
                 "observation":{"cellulesRoyales":6,"cellulesRoyalesCause":"essaimage",
                                "cadresCouvain":9,"couvainOpercule":true,"cadresMiel":8}}""")
                .formatted(rucheId, agentId, LocalDate.now().minusDays(1)));

        mockMvc.perform(get("/api/indices").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].composantes").value(
                        org.hamcrest.Matchers.greaterThan(0)))
                // 60 (cause essaimage) + 12 (six cellules) + 20 (couvain dense)
                // + 10 (corps plein) : le risque est au plafond.
                .andExpect(jsonPath("$[0].risqueEssaimage").value(100))
                // La sante, elle, reste bonne : une colonie qui va essaimer se
                // porte tres bien, et confondre les deux ferait rater l'essaim.
                .andExpect(jsonPath("$[0].sante").value(100));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long agent(String t) throws Exception {
        return creer(t, "/api/agents",
                "{\"nom\":\"Amine Trabelsi\",\"role\":\"apiculteur\",\"email\":null}");
    }

    private long chaineRuche(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        long fermeId = creer(t, "/api/fermes",
                "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
        long siteId = creer(t, "/api/sites",
                "{\"nom\":\"S\",\"fermeId\":" + fermeId
                        + ",\"latitude\":45.0,\"longitude\":1.0,"
                        + "\"dateMiseEnOeuvre\":\"2026-04-01\"}");
        return creer(t, "/api/ruches",
                "{\"modele\":\"M\",\"siteId\":" + siteId + ",\"fermeId\":" + fermeId
                        + ",\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}");
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
