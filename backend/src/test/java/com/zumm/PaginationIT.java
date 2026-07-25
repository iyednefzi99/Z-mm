package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * Verifie la pagination des endpoints de liste (US-052, SPRINT-11) sur un jeu
 * volumineux : 250 sites, soit dix pages de la taille par defaut.
 *
 * <p>Le point sensible n'est pas de couper la liste — c'est que l'isolation entre
 * tenants tienne <b>sur toutes les pages</b>, et pas seulement sur la premiere.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaginationIT {

    private static final int VOLUME = 250;

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

    @Test
    @DisplayName("sans parametre, rend la liste complete et son total — comportement d'avant")
    void sansParametreListeComplete() throws Exception {
        String t = "page-complet";
        peupler(t, 30);

        mockMvc.perform(get("/api/sites").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "30"))
                .andExpect(jsonPath("$.length()").value(30));
    }

    @Test
    @DisplayName("decoupe un jeu de 250 sites en pages et annonce le total")
    void decoupeUnGrosJeu() throws Exception {
        String t = "page-volume";
        peupler(t, VOLUME);

        mockMvc.perform(get("/api/sites").with(tenant(t))
                        .param("page", "0").param("taille", "25"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", String.valueOf(VOLUME)))
                .andExpect(header().string("X-Page", "0"))
                .andExpect(header().string("X-Taille", "25"))
                .andExpect(jsonPath("$.length()").value(25));

        // Page 3 : ni la premiere, ni la derniere — celle ou une erreur d'offset se voit.
        mockMvc.perform(get("/api/sites").with(tenant(t))
                        .param("page", "3").param("taille", "25").param("tri", "nom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25))
                // Tri par nom : la page 3 commence au 76e element (decalage 3 x 25).
                .andExpect(jsonPath("$[0].nom").value("Site 075"))
                .andExpect(jsonPath("$[24].nom").value("Site 099"));
    }

    @Test
    @DisplayName("rend une derniere page partielle, sans erreur")
    void dernierePagePartielle() throws Exception {
        String t = "page-fin";
        peupler(t, 30);

        mockMvc.perform(get("/api/sites").with(tenant(t))
                        .param("page", "1").param("taille", "25"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "30"))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    @DisplayName("rend une page vide au-dela du dernier element, pas une erreur")
    void auDelaDeLaFin() throws Exception {
        String t = "page-vide";
        peupler(t, 5);

        mockMvc.perform(get("/api/sites").with(tenant(t))
                        .param("page", "9").param("taille", "25"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "5"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("applique la taille par defaut de ConfigZumm.ini quand seule la page est donnee")
    void tailleParDefautDeLaConfiguration() throws Exception {
        String t = "page-defaut";
        peupler(t, 40);

        mockMvc.perform(get("/api/sites").with(tenant(t)).param("page", "0"))
                .andExpect(status().isOk())
                // taille_page_defaut = 25 dans le gabarit versionne.
                .andExpect(header().string("X-Taille", "25"))
                .andExpect(jsonPath("$.length()").value(25));
    }

    @Test
    @DisplayName("plafonne une taille de page demesuree plutot que de tout servir")
    void plafonneLaTaille() throws Exception {
        String t = "page-plafond";
        peupler(t, 30);

        mockMvc.perform(get("/api/sites").with(tenant(t))
                        .param("page", "0").param("taille", "100000"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Taille", "200"));
    }

    @Test
    @DisplayName("refuse une page negative ou une taille nulle")
    void parametresInvalides() throws Exception {
        String t = "page-invalide";
        peupler(t, 3);

        mockMvc.perform(get("/api/sites").with(tenant(t)).param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/sites").with(tenant(t)).param("taille", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("l'isolation entre tenants tient sur TOUTES les pages, pas seulement la premiere")
    void isolationSurToutesLesPages() throws Exception {
        String mien = "page-mien";
        String autre = "page-autre";
        peupler(mien, 60);
        peupler(autre, 60);

        // Le total ne compte que mes sites.
        mockMvc.perform(get("/api/sites").with(tenant(mien))
                        .param("page", "0").param("taille", "25"))
                .andExpect(header().string("X-Total-Count", "60"));

        // Et la derniere page ne deborde pas sur ceux du voisin.
        String derniere = mockMvc.perform(get("/api/sites").with(tenant(mien))
                        .param("page", "2").param("taille", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        assertTousDuTenant(derniere, mien);
    }

    /** Verifie qu'aucun site rendu n'appartient a un autre tenant. */
    private void assertTousDuTenant(String corpsJson, String tenantId) throws Exception {
        var noeuds = json.readTree(corpsJson);
        for (var site : noeuds) {
            String nomFerme = site.get("fermeNom").asText();
            if (!nomFerme.equals("Ferme " + tenantId)) {
                throw new AssertionError(
                        "Site d'un autre tenant dans la page : " + site.get("nom").asText());
            }
        }
    }

    /** Cree {@code combien} sites sous un tenant, numerotes de facon stable. */
    private void peupler(String tenantId, int combien) throws Exception {
        long fermierId = idApres(tenantId, "/api/fermiers",
                "{\"nom\":\"Fermier " + tenantId + "\",\"contact\":null}");
        long fermeId = idApres(tenantId, "/api/fermes",
                "{\"nom\":\"Ferme " + tenantId + "\",\"fermierId\":" + fermierId + "}");
        for (int i = 0; i < combien; i++) {
            idApres(tenantId, "/api/sites",
                    ("{\"nom\":\"Site %03d\",\"fermeId\":%d,\"latitude\":45.0,\"longitude\":1.0,"
                            + "\"dateMiseEnOeuvre\":\"2026-04-01\"}").formatted(i, fermeId));
        }
    }

    private long idApres(String t, String url, String corps) throws Exception {
        String reponse = mockMvc.perform(post(url).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(reponse).get("id").asLong();
    }
}
