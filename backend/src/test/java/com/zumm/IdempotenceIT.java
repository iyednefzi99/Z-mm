package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zumm.web.FiltreIdempotence;
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
 * Idempotence des mutations (US-055).
 *
 * <p>Le scenario couvert est celui du terrain : la PWA envoie une creation, le
 * reseau tombe APRES que le serveur l'a traitee, le client ne recoit pas la
 * reponse et rejoue au retour du reseau. Sans cle, deux fermiers sont crees ;
 * avec cle, un seul — et le client retrouve le meme identifiant.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IdempotenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("zumm/test-postgres:16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    private static final String TENANT = "exploitation-idempotence";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private static JwtRequestPostProcessor jeton() {
        return jwt().jwt(b -> b.claim("tenant_id", TENANT))
                .authorities(new SimpleGrantedAuthority("ROLE_admin"),
                        new SimpleGrantedAuthority("ROLE_responsable"));
    }

    private static String corpsFermier(String nom) {
        return "{\"nom\":\"" + nom + "\",\"contact\":\"contact@example.invalid\"}";
    }

    @Test
    @DisplayName("un rejeu avec la meme cle ne cree qu'une seule ressource")
    void rejeuNeCreeQuUneRessource() throws Exception {
        String cle = "cle-rejeu-1";
        String corps = corpsFermier("Rucher du rejeu");

        String premiere = mockMvc.perform(post("/api/fermiers").with(jeton())
                        .header(FiltreIdempotence.EN_TETE, cle)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String seconde = mockMvc.perform(post("/api/fermiers").with(jeton())
                        .header(FiltreIdempotence.EN_TETE, cle)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                // En-tete explicite : la reponse vient du magasin, pas d'un
                // nouveau traitement.
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andReturn().getResponse().getContentAsString();

        // Le client doit retrouver LE MEME identifiant : c'est ce qui garde son
        // etat local juste apres une synchronisation.
        JsonNode a = json.readTree(premiere);
        JsonNode b = json.readTree(seconde);
        assertThat(b.get("id").asLong()).isEqualTo(a.get("id").asLong());

        // Et une seule ressource existe reellement.
        String liste = mockMvc.perform(get("/api/fermiers").with(jeton()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long occurrences = json.readTree(liste).findValues("nom").stream()
                .filter(n -> "Rucher du rejeu".equals(n.asText()))
                .count();
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    @DisplayName("la meme cle reutilisee pour une autre requete est refusee")
    void cleReutiliseePourUneAutreRequete() throws Exception {
        String cle = "cle-conflit";

        mockMvc.perform(post("/api/fermiers").with(jeton())
                        .header(FiltreIdempotence.EN_TETE, cle)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsFermier("Premier")))
                .andExpect(status().isCreated());

        // Meme cle, corps different : c'est un bug client, pas un rejeu. Renvoyer
        // la reponse memorisee ferait croire a la creation d'un « Second » qui
        // n'existe pas.
        mockMvc.perform(post("/api/fermiers").with(jeton())
                        .header(FiltreIdempotence.EN_TETE, cle)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsFermier("Second")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("sans en-tete, le comportement anterieur est inchange")
    void sansEnTeteAucunChangement() throws Exception {
        // Le filtre ne doit rien couter ni rien changer aux clients qui ne
        // fournissent pas de cle.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/fermiers").with(jeton())
                            .contentType(MediaType.APPLICATION_JSON).content(corpsFermier("Sans cle")))
                    .andExpect(status().isCreated());
        }
        String liste = mockMvc.perform(get("/api/fermiers").with(jeton()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long occurrences = json.readTree(liste).findValues("nom").stream()
                .filter(n -> "Sans cle".equals(n.asText()))
                .count();
        assertThat(occurrences).isEqualTo(2);
    }

    @Test
    @DisplayName("une cle vide est refusee")
    void cleVideRefusee() throws Exception {
        mockMvc.perform(post("/api/fermiers").with(jeton())
                        .header(FiltreIdempotence.EN_TETE, " ")
                        .contentType(MediaType.APPLICATION_JSON).content(corpsFermier("Vide")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une reponse en echec n'est pas memorisee")
    void echecNonMemorise() throws Exception {
        String cle = "cle-echec";
        // Corps invalide : 400. Memoriser cet echec figerait une erreur passagere
        // en verdict definitif — le client corrige et rejoue avec la meme cle.
        mockMvc.perform(post("/api/fermiers").with(jeton())
                        .header(FiltreIdempotence.EN_TETE, cle)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/fermiers").with(jeton())
                        .header(FiltreIdempotence.EN_TETE, cle)
                        .contentType(MediaType.APPLICATION_JSON).content(corpsFermier("Corrige")))
                .andExpect(status().isCreated());
    }
}
