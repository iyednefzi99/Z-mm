package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Éprouve le SPRINT-26, lot F₁ — capteurs : alimentation, poids par hausse,
 * partage de flux.
 *
 * <p>Quatre comportements s'y jouent, et ce sont ceux qui ne se voient qu'en
 * base ou qu'à la sortie :
 *
 * <ol>
 *   <li>une batterie faible ouvre une alerte comme n'importe quel autre seuil —
 *       c'est le reproche fait à BeeLog et Onibi, la panne <em>silencieuse</em> ;
 *   <li>le poids par hausse vit dans une table à part et <strong>ne contamine
 *       pas</strong> la série de la ruche : c'est tout l'objet du choix ;
 *   <li>un partage se lit <strong>sans session</strong>, ne rend ni identifiant
 *       ni position, et cesse de répondre dès qu'il est révoqué ;
 *   <li>la gestion des partages, elle, reste derrière l'authentification — la
 *       liste des partages d'une ruche est une liste de clefs.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TelemetrieIT {

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

    // ─── Alimentation des capteurs ───────────────────────────────────────────

    @Test
    @DisplayName("une batterie sous le seuil ouvre une alerte, comme n'importe quel indicateur")
    void alerteDeBatterie() throws Exception {
        String t = "sp26-batterie";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        // 12 % : sous le seuil de 20 % de ConfigZumm.ini. La panne silencieuse
        // est le reproche fait à BeeLog et à Onibi — pas l'absence de mesure,
        // l'absence d'ALERTE.
        mockMvc.perform(post("/api/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"typeIndicateur":"alimentation","valeur":12}""")
                                .formatted(rucheId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertes.length()").value(1))
                .andExpect(jsonPath("$.alertes[0].message").value(
                        org.hamcrest.Matchers.containsString("Batterie")));

        // Remontée franche au-dessus du seuil : l'hystérésis referme, sans
        // qu'un second mécanisme d'alerte ait eu à être écrit.
        mockMvc.perform(post("/api/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"typeIndicateur":"alimentation","valeur":95}""")
                                .formatted(rucheId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertes.length()").value(1));

        mockMvc.perform(get("/api/mesures/alertes").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Poids par hausse ────────────────────────────────────────────────────

    @Test
    @DisplayName("le poids d'une hausse ne contamine pas la série de la ruche")
    void poidsParHausse() throws Exception {
        String t = "sp26-hausse";
        long fermeId = ferme(t);
        long rucheId = creer(t, "/api/ruches", ("""
                {"modele":"Dadant","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10},
                                  {"type":"hausse","nbCadres":9}]}""")
                .formatted(site(t, fermeId), fermeId));

        String composition = mockMvc.perform(get("/api/ruches/" + rucheId).with(tenant(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long hausse = json.readTree(composition).get("compartiments").get(1).get("id").asLong();

        mockMvc.perform(post("/api/compartiments/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compartimentId\":%d,\"valeur\":14.5}".formatted(hausse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("hausse"))
                .andExpect(jsonPath("$.valeur").value(14.5));

        // Le point décisif : la série de la RUCHE ignore ce poids. Les mélanger
        // ferait compter deux fois le même miel dans la prévision de récolte.
        mockMvc.perform(get("/api/mesures?rucheId=" + rucheId + "&type=poids")
                        .with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/compartiments/repartition?rucheId=" + rucheId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Le corps n'a jamais été pesé : son poids est NUL, pas zéro.
                // Une hausse jamais pesée n'est pas une hausse vide.
                .andExpect(jsonPath("$[0].type").value("corps"))
                .andExpect(jsonPath("$[0].valeur").doesNotExist())
                .andExpect(jsonPath("$[1].type").value("hausse"))
                .andExpect(jsonPath("$[1].valeur").value(14.5));
    }

    @Test
    @DisplayName("un poids négatif est refusé, et un compartiment inconnu aussi")
    void peseesRefusees() throws Exception {
        String t = "sp26-refus";
        long fermeId = ferme(t);
        long rucheId = creer(t, "/api/ruches", ("""
                {"modele":"Dadant","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10}]}""")
                .formatted(site(t, fermeId), fermeId));
        String composition = mockMvc.perform(get("/api/ruches/" + rucheId).with(tenant(t)))
                .andReturn().getResponse().getContentAsString();
        long corps = json.readTree(composition).get("compartiments").get(0).get("id").asLong();

        // `NUMERIC` est signé : la base l'accepterait, et la courbe deviendrait
        // illisible.
        mockMvc.perform(post("/api/compartiments/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compartimentId\":%d,\"valeur\":-3}".formatted(corps)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/compartiments/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"compartimentId\":999999,\"valeur\":10}"))
                .andExpect(status().isBadRequest());
    }

    // ─── Partage d'un flux ───────────────────────────────────────────────────

    @Test
    @DisplayName("un partage se lit sans session, sans identifiant et sans position")
    void partageLuSansSession() throws Exception {
        String t = "sp26-partage";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        mockMvc.perform(post("/api/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"typeIndicateur":"poids","valeur":42.5,
                                 "instant":"%s"}""")
                                .formatted(rucheId, Instant.now().truncatedTo(ChronoUnit.SECONDS))))
                .andExpect(status().isCreated());

        String cree = mockMvc.perform(post("/api/partages").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"libelle":"Technicien sanitaire",
                                 "dureeJours":30}""").formatted(rucheId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actif").value(true))
                .andReturn().getResponse().getContentAsString();

        long partageId = json.readTree(cree).get("id").asLong();
        String url = json.readTree(cree).get("url").asText();
        String jeton = url.substring(url.lastIndexOf('/') + 1);
        // 32 octets en base64 sans remplissage : indevinable, même sans
        // limitation de débit.
        assertThat(jeton).hasSizeGreaterThan(40);

        // SANS `.with(...)` : aucune session, aucun jeton d'accès, aucun tenant.
        mockMvc.perform(get("/api/flux/" + jeton))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Technicien sanitaire"))
                .andExpect(jsonPath("$.serie.length()").value(1))
                // Ce que le destinataire NE reçoit pas : il regarde une courbe,
                // il n'explore pas un cheptel.
                .andExpect(jsonPath("$.rucheId").doesNotExist())
                .andExpect(jsonPath("$.siteNom").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist());

        // L'usage est visible : c'est ce qui rend la révocation décidable.
        mockMvc.perform(get("/api/partages?rucheId=" + rucheId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].derniereUtilisation").exists())
                // Le jeton n'est jamais rendu deux fois : la base n'en a que
                // l'empreinte.
                .andExpect(jsonPath("$[0].url").doesNotExist());

        mockMvc.perform(delete("/api/partages/" + partageId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        // Révoqué : 404, et non 403. Distinguer les refus confirmerait qu'un
        // jeton a existé, ce qui est déjà une information de trop.
        mockMvc.perform(get("/api/flux/" + jeton))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un jeton inconnu répond 404, comme un jeton révoqué")
    void jetonInconnu() throws Exception {
        mockMvc.perform(get("/api/flux/jeton-qui-n-a-jamais-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la LISTE des partages, elle, exige une session : c'est une liste de clefs")
    void gestionProtegee() throws Exception {
        String t = "sp26-partage-rbac";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        // 401 sur la lecture : sans jeton, le serveur de ressources refuse avant
        // d'evaluer le moindre role. La creation, elle, est arretee par la
        // protection CSRF — 403. Les deux refus sont justes, et ils ne viennent
        // pas du meme endroit.
        mockMvc.perform(get("/api/partages?rucheId=" + rucheId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/partages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rucheId\":1,\"libelle\":\"x\",\"dureeJours\":30}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("partager la ruche d'une autre exploitation échoue avant toute écriture")
    void partageHorsTenant() throws Exception {
        String t = "sp26-partage-tenant";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        mockMvc.perform(post("/api/partages").with(tenant("sp26-autre"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"libelle":"Vol de courbe","dureeJours":30}""")
                                .formatted(rucheId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Jeu d'essai ─────────────────────────────────────────────────────────

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

    private long creer(String t, String url, String corps) throws Exception {
        MockHttpServletRequestBuilder requete = post(url).with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON).content(corps);
        String rep = mockMvc.perform(requete)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("id").asLong();
    }
}
