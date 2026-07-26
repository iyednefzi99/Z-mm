package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Conformite de la mention d'origine (US-056) a la directive (UE) 2024/1438,
 * applicable au 14 juin 2026.
 *
 * <p>Les cas testes sont ceux qu'un controle regarde : un lot mono-origine, un
 * melange dont les parts doivent sortir dans l'ordre DECROISSANT, la consolidation
 * de plusieurs recoltes d'un meme pays en une seule ligne d'etiquette, et le refus
 * d'un lot dont les parts ne totalisent pas 100 %.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ConformiteMielIT {

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

    private static JwtRequestPostProcessor jeton() {
        return jwt().jwt(b -> b.claim("tenant_id", "exploitation-miel"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin"),
                        new SimpleGrantedAuthority("ROLE_responsable"));
    }

    /**
     * Cree un lot et renvoie son identifiant. Lecture par Jackson et non par
     * expression reguliere : la reponse contient AUSSI un `id` par part de
     * composition, et une capture gloutonne prendrait la derniere.
     */
    private long creerLot(String reference, String origines) throws Exception {
        String corps = mockMvc.perform(post("/api/lots").with(jeton())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference":"%s","dateConditionnement":"2026-08-15",
                                 "quantiteKg":120.5,"typeMiel":"Toutes fleurs",
                                 "origines":%s}
                                """.formatted(reference, origines)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(corps).get("id").asLong();
    }

    @Test
    @DisplayName("un lot mono-origine porte la mention simple, sans pourcentage")
    void lotMonoOrigine() throws Exception {
        long id = creerLot("LOT-2026-001", """
                [{"paysOrigine":"FR","pourcentage":100}]""");

        // La directive n'impose le pourcentage que pour les MELANGES : afficher
        // « France 100 % » serait exact mais inutilement bavard sur un pot francais.
        mockMvc.perform(get("/api/lots/" + id + "/mention").with(jeton())
                        .header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.melange").value(false))
                .andExpect(jsonPath("$.texte").value("Origine : France"))
                .andExpect(jsonPath("$.origines.length()").value(1));
    }

    @Test
    @DisplayName("un melange liste les pays par proportion decroissante")
    void melangeOrdreDecroissant() throws Exception {
        // Les parts sont DECLAREES dans le desordre : c'est le service qui doit
        // les remettre dans l'ordre exige, pas l'utilisateur.
        long id = creerLot("LOT-2026-002", """
                [{"paysOrigine":"ES","pourcentage":25},
                 {"paysOrigine":"UA","pourcentage":15},
                 {"paysOrigine":"FR","pourcentage":60}]""");

        mockMvc.perform(get("/api/lots/" + id + "/mention").with(jeton())
                        .header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.melange").value(true))
                .andExpect(jsonPath("$.origines[0].paysOrigine").value("FR"))
                .andExpect(jsonPath("$.origines[1].paysOrigine").value("ES"))
                .andExpect(jsonPath("$.origines[2].paysOrigine").value("UA"))
                .andExpect(jsonPath("$.texte").value("Origine : France 60 %, Espagne 25 %, Ukraine 15 %"));
    }

    @Test
    @DisplayName("plusieurs parts d'un meme pays sont consolidees en une seule ligne")
    void consolidationParPays() throws Exception {
        // Cas courant : trois recoltes francaises entrent dans le meme lot.
        // L'etiquette dit « France 75 % », pas « France 30 %, France 25 %, France 20 % ».
        long id = creerLot("LOT-2026-003", """
                [{"paysOrigine":"FR","pourcentage":30},
                 {"paysOrigine":"FR","pourcentage":25},
                 {"paysOrigine":"FR","pourcentage":20},
                 {"paysOrigine":"IT","pourcentage":25}]""");

        mockMvc.perform(get("/api/lots/" + id + "/mention").with(jeton())
                        .header("Accept-Language", "fr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origines.length()").value(2))
                .andExpect(jsonPath("$.origines[0].paysOrigine").value("FR"))
                .andExpect(jsonPath("$.origines[0].pourcentage").value(75));
    }

    @Test
    @DisplayName("la mention est rendue dans la langue demandee")
    void mentionTraduite() throws Exception {
        long id = creerLot("LOT-2026-004", """
                [{"paysOrigine":"FR","pourcentage":100}]""");

        // Un miel exporte s'etiquette dans la langue du marche.
        mockMvc.perform(get("/api/lots/" + id + "/mention").with(jeton())
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origines[0].libelle").value("France"));
    }

    @Test
    @DisplayName("un lot dont les parts ne totalisent pas 100 % est refuse")
    void sommeInvalideRefusee() throws Exception {
        // Le lot non conforme doit etre impossible a enregistrer : le laisser
        // passer « pour completer plus tard » produirait une etiquette fausse.
        mockMvc.perform(post("/api/lots").with(jeton())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference":"LOT-2026-005","dateConditionnement":"2026-08-15",
                                 "quantiteKg":50,
                                 "origines":[{"paysOrigine":"FR","pourcentage":60},
                                             {"paysOrigine":"ES","pourcentage":25}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("85")));
    }

    @Test
    @DisplayName("un lot sans aucune origine est refuse")
    void lotSansOrigineRefuse() throws Exception {
        mockMvc.perform(post("/api/lots").with(jeton())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference":"LOT-2026-006","dateConditionnement":"2026-08-15",
                                 "quantiteKg":50,"origines":[]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un code pays hors norme ISO est refuse")
    void codePaysInvalideRefuse() throws Exception {
        // « FRA » ou « france » rendraient l'etiquette intraduisible et le
        // regroupement par pays inoperant.
        mockMvc.perform(post("/api/lots").with(jeton())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reference":"LOT-2026-007","dateConditionnement":"2026-08-15",
                                 "quantiteKg":50,
                                 "origines":[{"paysOrigine":"FRA","pourcentage":100}]}
                                """))
                .andExpect(status().isBadRequest());
    }
}
