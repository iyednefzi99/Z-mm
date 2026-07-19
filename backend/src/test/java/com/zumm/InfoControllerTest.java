package com.zumm;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zumm.config.ZummProperties;
import com.zumm.controller.InfoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tranche web : verifie le contrat de l'endpoint d'identite et l'aiguillage
 * d'internationalisation, sans dependre d'une base de donnees.
 *
 * <p>La chaine complete (Flyway, JPA, PostgreSQL) est couverte par
 * {@link WalkingSkeletonIT}, qui exige Docker.
 */
@WebMvcTest(controllers = InfoController.class)
@EnableConfigurationProperties(ZummProperties.class)
// Filtres de securite desactives : ce test verifie le contrat de l'API et
// l'internationalisation. Les regles d'acces sont couvertes par SecuriteApiIT.
@AutoConfigureMockMvc(addFilters = false)
class InfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("expose l'identite de l'application et ses trois langues")
    void exposeIdentiteEtLangues() throws Exception {
        mockMvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Zumm"))
                .andExpect(jsonPath("$.langues").isArray())
                .andExpect(jsonPath("$.langues.length()").value(3));
    }

    @Test
    @DisplayName("repond en francais quand aucune langue n'est demandee")
    void repondEnFrancaisParDefaut() throws Exception {
        mockMvc.perform(get("/api/info"))
                .andExpect(jsonPath("$.accueil").value("Bienvenue dans Zümm"));
    }

    @Test
    @DisplayName("traduit l'accueil selon l'en-tete Accept-Language")
    void traduitSelonAcceptLanguage() throws Exception {
        mockMvc.perform(get("/api/info").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.accueil").value("Welcome to Zümm"));

        mockMvc.perform(get("/api/info").header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
                .andExpect(jsonPath("$.accueil").value("مرحبا بكم في زوم"));
    }
}
