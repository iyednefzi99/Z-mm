package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zumm.repository.InvitationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Codes d'invitation, de l'emission a l'epuisement (US-058, ADR-009).
 *
 * <p>C'est le test qui prouve la partie que rien d'autre ne couvre : la
 * reservation passe par une fonction {@code SECURITY DEFINER} appelee SANS
 * contexte de tenant — un chemin qui, par construction, echappe a la RLS. Il
 * faut donc verifier ici, contre un vrai PostgreSQL, que cette derogation reste
 * etroite : elle rend le rattachement du code presente, et rien d'autre.
 *
 * <p>Trois proprietes verifiees, chacune correspondant a un defaut possible :
 *
 * <ul>
 *   <li>un code epuise ne reserve plus — sinon une invitation a une place en
 *       ouvrirait autant qu'on veut ;
 *   <li>un responsable ne voit pas les codes d'une autre exploitation — la
 *       liste des codes est une liste de clefs valides ;
 *   <li>un apiculteur ne peut ni lister ni emettre — decider qui entre dans le
 *       cheptel est une prerogative de responsable.
 * </ul>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InvitationIT {

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

    @Autowired
    private InvitationRepository invitations;

    private JwtRequestPostProcessor tenant(String tid, String... roles) {
        GrantedAuthority[] autorites = java.util.Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toArray(GrantedAuthority[]::new);
        return jwt().jwt(b -> b.claim("tenant_id", tid).claim("preferred_username", "resp"))
                .authorities(autorites);
    }

    /** Emet un code et rend sa valeur. */
    private String emettre(String tid, String role, int places) throws Exception {
        String reponse = mockMvc.perform(post("/api/invitations").with(tenant(tid, "responsable"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"" + role + "\",\"utilisationsMax\":" + places
                                + ",\"joursValidite\":14}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode corps = json.readTree(reponse);
        assertThat(corps.get("code").asText()).startsWith("ZM-");
        return corps.get("code").asText();
    }

    @Test
    @DisplayName("une invitation a une place ne se reserve qu'une fois")
    void invitationEpuiseeNeReservePlus() throws Exception {
        String code = emettre("inv-a", "apiculteur", 1);

        var premiere = invitations.reserver(code);
        assertThat(premiere).isPresent();
        assertThat(premiere.get().tenantId()).isEqualTo("inv-a");
        assertThat(premiere.get().role()).isEqualTo("apiculteur");

        // Deuxieme tentative sur la meme place : c'est ici que se joue la
        // difference entre « verifier puis consommer » et un seul ordre atomique.
        assertThat(invitations.reserver(code)).isEmpty();

        // Relachee — creation de compte echouee — la place redevient disponible.
        invitations.relacher(code);
        assertThat(invitations.reserver(code)).isPresent();
    }

    @Test
    @DisplayName("un code inconnu ou mal forme ne rend rien, sans erreur")
    void codeInconnuNeRendRien() {
        assertThat(invitations.reserver("ZM-INEXISTANT")).isEmpty();
        assertThat(invitations.reserver("")).isEmpty();
        assertThat(invitations.reserver(null)).isEmpty();
    }

    @Test
    @DisplayName("la saisie est normalisee : minuscules et espaces sont tolerés")
    void codeNormaliseALaReservation() throws Exception {
        String code = emettre("inv-b", "superviseur", 2);

        // Le code est lu sur un papier : exiger la casse exacte ferait echouer
        // une inscription pour une raison que l'utilisateur ne peut pas voir.
        assertThat(invitations.reserver("  " + code.toLowerCase() + " ")).isPresent();
    }

    @Test
    @DisplayName("un responsable ne voit que les codes de son exploitation")
    void listeCloisonneeParExploitation() throws Exception {
        emettre("inv-c", "apiculteur", 1);
        emettre("inv-d", "apiculteur", 1);

        mockMvc.perform(get("/api/invitations").with(tenant("inv-c", "responsable")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("emettre et lister sont refuses a l'apiculteur")
    void reserveAuResponsable() throws Exception {
        mockMvc.perform(get("/api/invitations").with(tenant("inv-e", "apiculteur")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/invitations").with(tenant("inv-e", "apiculteur"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"apiculteur\",\"utilisationsMax\":1,\"joursValidite\":7}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("une invitation ne peut pas fabriquer un administrateur")
    void roleAdminNonInvitable() throws Exception {
        mockMvc.perform(post("/api/invitations").with(tenant("inv-f", "responsable"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"admin\",\"utilisationsMax\":1,\"joursValidite\":7}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un code revoque ne reserve plus rien")
    void revocationFermeLaPorte() throws Exception {
        String code = emettre("inv-g", "apiculteur", 3);
        String liste = mockMvc.perform(get("/api/invitations").with(tenant("inv-g", "responsable")))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(liste).get(0).get("id").asLong();

        mockMvc.perform(delete("/api/invitations/" + id).with(tenant("inv-g", "responsable")))
                .andExpect(status().isNoContent());

        assertThat(invitations.reserver(code)).isEmpty();
    }

    @Test
    @DisplayName("la validite demandee est bornee, pas recopiee")
    void validiteBornee() throws Exception {
        // Une invitation valable mille jours est une porte laissee ouverte ; la
        // borne est posee par le service, pas par la confiance dans le client.
        mockMvc.perform(post("/api/invitations").with(tenant("inv-h", "responsable"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"apiculteur\",\"utilisationsMax\":1,\"joursValidite\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.epuise").value(false));

        String liste = mockMvc.perform(get("/api/invitations").with(tenant("inv-h", "responsable")))
                .andReturn().getResponse().getContentAsString();
        String expire = json.readTree(liste).get(0).get("expireLe").asText();
        assertThat(java.time.Instant.parse(expire))
                .isBefore(java.time.Instant.now().plus(java.time.Duration.ofDays(91)));
    }
}
