package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
 * Éprouve le SPRINT-31, lot F₂ — alarme anti-vol, inclinaison, ingestion par lot.
 *
 * <p>Tout ce qui est vérifié ici tient à une seule phrase, et c'est elle qui
 * rend l'alarme utilisable : <strong>une chute de vingt kilogrammes est une
 * récolte si une récolte a été enregistrée ce jour-là, et un vol sinon</strong>.
 * Sans cette distinction, la première miellée réveillerait l'alarme sur tout le
 * rucher — et une alarme qui sonne pour rien se coupe.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AntivolCapteursIT {

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

    /** Deux pesées espacées d'une heure, la seconde plus basse de `chute` kg. */
    private void peser(String t, long rucheId, double avant, double apres) throws Exception {
        Instant premiere = Instant.parse("2026-07-15T06:00:00Z");
        mesurer(t, rucheId, "poids", avant, premiere);
        mesurer(t, rucheId, "poids", apres, premiere.plusSeconds(3600));
    }

    private void mesurer(String t, long rucheId, String type, double valeur, Instant instant)
            throws Exception {
        mockMvc.perform(post("/api/mesures").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"typeIndicateur":"%s","valeur":%s,"instant":"%s"}""")
                                .formatted(rucheId, type, valeur, instant)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("une chute brutale sans recolte ouvre une alerte de vol")
    void chuteSansRecolte() throws Exception {
        String t = "sp31-vol";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        peser(t, rucheId, 45.0, 8.0);

        mockMvc.perform(get("/api/mesures/alertes").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.categorie == 'antivol')].niveau").value("critique"))
                .andExpect(jsonPath("$[?(@.categorie == 'antivol')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("sans recolte"))));
    }

    @Test
    @DisplayName("la meme chute, un jour de recolte, ne reveille rien")
    void chuteExpliqueeParUneRecolte() throws Exception {
        String t = "sp31-recolte";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        // LA verification qui rend l'alarme utilisable. Sans elle, la premiere
        // miellee de l'annee reveillerait l'alarme sur tout le rucher, et
        // l'apiculteur la couperait — apres quoi elle ne protege plus de rien.
        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-07-15","quantiteKg":30}""").formatted(rucheId));

        peser(t, rucheId, 45.0, 8.0);

        mockMvc.perform(get("/api/mesures/alertes").with(tenant(t)))
                .andExpect(jsonPath("$[?(@.categorie == 'antivol')]").isEmpty());
    }

    @Test
    @DisplayName("une ruche peut etre legere ET volee : les deux alertes coexistent")
    void seuilEtVolCoexistent() throws Exception {
        String t = "sp31-deux";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        // 8 kg est sous le seuil de poids par defaut (15 kg) : l'alerte de seuil
        // s'ouvre. Avant le SPRINT-31, elle aurait EMPECHE l'alerte de vol de
        // s'ouvrir — au moment precis ou elle sert.
        peser(t, rucheId, 45.0, 8.0);

        mockMvc.perform(get("/api/mesures/alertes").with(tenant(t)))
                .andExpect(jsonPath("$[?(@.categorie == 'seuil')]")
                        .value(org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.categorie == 'antivol')]")
                        .value(org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("une baisse ordinaire ne declenche rien")
    void baisseOrdinaire() throws Exception {
        String t = "sp31-normal";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        // Deux kilos en une heure : une colonie qui butine, pas un vol.
        peser(t, rucheId, 45.0, 43.0);

        mockMvc.perform(get("/api/mesures/alertes").with(tenant(t)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("l'inclinaison ouvre une alerte, et la contrainte de la table l'accepte")
    void inclinaison() throws Exception {
        String t = "sp31-inclinaison";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        // La lecon de la V26 : `alimentation` avait ete ajoute a la contrainte
        // des MESURES sans l'etre a celle des ALERTES, et l'ingestion reussissait
        // pendant que l'ouverture de l'alerte echouait en 409. Ce test verifie
        // les deux d'un coup.
        mesurer(t, rucheId, "inclinaison", 35.0, Instant.parse("2026-07-15T06:00:00Z"));

        mockMvc.perform(get("/api/mesures/alertes").with(tenant(t)))
                .andExpect(jsonPath("$[?(@.typeIndicateur == 'inclinaison')].niveau")
                        .value("critique"));
    }

    @Test
    @DisplayName("une passerelle pousse tout son releve en une requete")
    void ingestionParLot() throws Exception {
        String t = "sp31-lot";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        mockMvc.perform(post("/api/mesures/lot").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                [{"rucheId":%d,"typeIndicateur":"temperature","valeur":34.2,
                                  "instant":"2026-07-15T06:00:00Z"},
                                 {"rucheId":%d,"typeIndicateur":"humidite","valeur":62,
                                  "instant":"2026-07-15T06:00:00Z"},
                                 {"rucheId":%d,"typeIndicateur":"alimentation","valeur":78,
                                  "instant":"2026-07-15T06:00:00Z"}]""")
                                .formatted(rucheId, rucheId, rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Un lot vide n'est pas un lot : le refuser evite qu'une passerelle en
        // panne pousse indefiniment des requetes sans contenu.
        mockMvc.perform(post("/api/mesures/lot").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
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

    private long creer(String t, String url, String corps) throws Exception {
        MockHttpServletRequestBuilder requete = post(url).with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON).content(corps);
        String rep = mockMvc.perform(requete)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("id").asLong();
    }
}
