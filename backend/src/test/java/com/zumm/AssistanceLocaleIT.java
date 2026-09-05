package com.zumm;

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
 * Éprouve le SPRINT-30, lot G — briefing du jour et mode local.
 *
 * <p>Le mode local est réglé à FAUX pour cette classe entière : c'est le seul
 * moyen d'éprouver la promesse « aucun appel sortant », qui ne se voit que
 * lorsqu'elle est active.
 *
 * <ol>
 *   <li>le briefing ne dit que ce que les registres portent, et le CITE ;
 *   <li>une carence qui se termine dans la semaine remonte — c'est ce que le
 *       registre ne donne pas de lui-même ;
 *   <li>{@code /api/info} publie l'état du réseau sortant, pour que l'écran
 *       puisse l'afficher au lieu de le promettre en prose.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs",
        // Mode local : la moitie serveur de la ligne §8.
        "zumm.reseau.sortant=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AssistanceLocaleIT {

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
                        new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    @Test
    @DisplayName("le serveur publie l'etat de son reseau sortant, sans authentification")
    void reseauSortantPublie() throws Exception {
        // Route publique : c'est une promesse a afficher, pas un secret. Un
        // exploitant qui a coupe le reseau veut le voir ecrit, et celui qui ne
        // l'a pas coupe ne doit pas croire l'avoir fait.
        mockMvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reseauSortant").value(false));
    }

    @Test
    @DisplayName("la meteo ne rend rien en mode local, et ce n'est pas une erreur")
    void meteoMuetteEnModeLocal() throws Exception {
        String t = "sp30-meteo";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);

        // Le fournisseur refuse de sortir, et la reponse emprunte le chemin
        // deja ecrit pour une panne reseau : la simulation deterministe.
        //
        // Ce qui compte ici, et qui est le point de toute la ligne §8 : la
        // reponse DIT d'ou vient le nombre. Une meteo simulee presentee comme
        // relevee serait pire qu'une meteo absente — c'est exactement le grief
        // que `vite.config.ts` formule contre le cache d'API depuis le
        // SPRINT-13.
        mockMvc.perform(get("/api/meteo").with(tenant(t)).param("siteId", String.valueOf(siteId)))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.source").value("simulation"));
    }

    @Test
    @DisplayName("un briefing sans rien a dire le dit, plutot que de meubler")
    void briefingCalme() throws Exception {
        String t = "sp30-calme";

        mockMvc.perform(get("/api/briefing").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes.length()").value(0));
    }

    @Test
    @DisplayName("le briefing annonce les carences qui se terminent dans la semaine")
    void briefingCarence() throws Exception {
        String t = "sp30-carence";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);
        long agentId = agent(t);
        LocalDate aujourdHui = LocalDate.of(2026, 9, 5);

        // Traitement termine le 1er, carence de 10 jours : retrait le 11, soit
        // dans six jours. C'est l'information que le registre ne donne pas de
        // lui-meme — savoir qu'une ruche EST sous carence est facile, savoir
        // qu'elle en SORT jeudi demande de calculer.
        creer(t, "/api/traitements", ("""
                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                 "dateDebut":"2026-08-01","dateFin":"2026-09-01","delaiCarenceJours":10}""")
                .formatted(rucheId, agentId));

        mockMvc.perform(get("/api/briefing").with(tenant(t))
                        .param("jour", aujourdHui.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[?(@.categorie == 'carence')].rucheId")
                        .value((int) rucheId))
                // Chaque ligne CITE ce qui la fonde : sans le detail, elle ne
                // serait qu'une opinion.
                .andExpect(jsonPath("$.lignes[?(@.categorie == 'carence')].detail")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("Apivar"))));
    }

    @Test
    @DisplayName("les taches en retard tiennent en une ligne, pas quarante")
    void briefingTaches() throws Exception {
        String t = "sp30-taches";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);
        long agentId = agent(t);

        creer(t, "/api/taches", ("""
                {"libelle":"Poser les hausses","agentId":%d,"rucheId":%d,
                 "echeance":"2026-08-01"}""").formatted(agentId, rucheId));
        creer(t, "/api/taches", ("""
                {"libelle":"Nettoyer les planchers","agentId":%d,"rucheId":%d,
                 "echeance":"2026-08-15"}""").formatted(agentId, rucheId));

        mockMvc.perform(get("/api/briefing").with(tenant(t)).param("jour", "2026-09-05"))
                .andExpect(status().isOk())
                // Une exploitation qui a quarante taches en retard n'a pas
                // quarante choses a savoir : elle en a une. Deux taches echues,
                // UNE ligne — et le compte est dans le titre.
                .andExpect(jsonPath("$.lignes[?(@.categorie == 'tache')]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.lignes[?(@.categorie == 'tache')].titre")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("2 tache"))));
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private long ferme(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        return creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
    }

    private long site(String t, long fermeId) throws Exception {
        return creer(t, "/api/sites", ("""
                {"nom":"S","fermeId":%d,"latitude":44.5,"longitude":1.5,
                 "dateMiseEnOeuvre":"2026-04-01"}""").formatted(fermeId));
    }

    private long ruche(String t, long siteId, long fermeId) throws Exception {
        return creer(t, "/api/ruches", ("""
                {"modele":"M","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10}]}""")
                .formatted(siteId, fermeId));
    }

    private long agent(String t) throws Exception {
        return creer(t, "/api/agents", """
                {"nom":"Agent","role":"apiculteur","email":"agent@exemple.test"}""");
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
