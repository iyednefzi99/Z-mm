package com.zumm;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zumm.controller.IdentiteBffController;
import com.zumm.service.IdentiteService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrat des deux routes d'entree (ADR-009).
 *
 * <p>Ce qui est verifie ici n'est pas le dialogue avec Keycloak — il est double —
 * mais ce que le SERVEUR laisse sortir. Trois proprietes, chacune capable de
 * disparaitre a la faveur d'un refactoring sans que rien d'autre n'echoue :
 *
 * <ul>
 *   <li>le corps d'un refus ne porte QUE le code convenu : ni message du
 *       fournisseur, ni trace technique, ni jeton ;
 *   <li>un compte inconnu et un mot de passe faux produisent la MEME reponse ;
 *   <li>un mot de passe trop court est refuse ici, sans appel au fournisseur.
 * </ul>
 */
@WebMvcTest(controllers = IdentiteBffController.class)
@AutoConfigureMockMvc(addFilters = false)
class IdentiteBffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentiteService identite;

    @MockitoBean
    private JwtDecoder decodeur;

    /** Jeton d'acces double : les claims dont l'ouverture de session a besoin. */
    private static Jwt jetonValide() {
        return Jwt.withTokenValue("jeton-double")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "9f1c-…")
                .claim("preferred_username", "zoubeir")
                .claim("tenant_id", "exploitation-demo")
                .claim("realm_access", Map.of("roles", List.of("apiculteur")))
                .build();
    }

    @Test
    @DisplayName("ouvre une session sans jamais renvoyer de jeton au navigateur")
    void connexionOuvreUneSession() throws Exception {
        given(identite.connexion("zoubeir", "ruche-sans-fin-2026"))
                .willReturn(new IdentiteService.Jetons("acces", "identite", "rafraichissement", 300));
        given(decodeur.decode("acces")).willReturn(jetonValide());

        mockMvc.perform(post("/bff/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifiant":"zoubeir","motDePasse":"ruche-sans-fin-2026"}"""))
                .andExpect(status().isNoContent())
                // 204 sans corps : le seul retour est le cookie de session, pose
                // par le depot de contexte. Un jeton qui apparaitrait ici serait
                // le retour exact de ce que l'ADR-006 a supprime.
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("refuse sans distinguer un compte inconnu d'un mot de passe faux")
    void connexionRefuseeResteIndistincte() throws Exception {
        willThrow(new IdentiteService.EchecIdentite(
                IdentiteService.Echec.IDENTIFIANTS_INVALIDES))
                .given(identite).connexion(anyString(), anyString());

        mockMvc.perform(post("/bff/connexion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifiant":"inconnu","motDePasse":"peu-importe-mais-long"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("identifiants-invalides"))
                // Le corps ne porte QUE le code : pas de champ `message`, ou le
                // fournisseur pourrait glisser « user not found ».
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    @DisplayName("traduit chaque motif d'echec en statut, sans en inventer")
    void motifsTraduitsEnStatuts() throws Exception {
        verifierMotif(IdentiteService.Echec.CODE_INCONNU, 422, "code-inconnu");
        verifierMotif(IdentiteService.Echec.COURRIEL_DEJA_PRIS, 409, "courriel-deja-pris");
        verifierMotif(IdentiteService.Echec.MOT_DE_PASSE_REFUSE, 422, "mot-de-passe-refuse");
        verifierMotif(IdentiteService.Echec.INDISPONIBLE, 503, "indisponible");
    }

    private void verifierMotif(IdentiteService.Echec motif, int statut, String code)
            throws Exception {
        willThrow(new IdentiteService.EchecIdentite(motif))
                .given(identite).inscription(anyString(), anyString(), anyString(), anyString());

        mockMvc.perform(post("/bff/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Nour Ben Salah","courriel":"nour@example.tn",
                                 "motDePasse":"ruche-sans-fin-2026","code":"ZM-4712"}"""))
                .andExpect(status().is(statut))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    @DisplayName("refuse un mot de passe trop court sans deranger le fournisseur")
    void motDePasseTropCourtRefuseAvantAppel() throws Exception {
        mockMvc.perform(post("/bff/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Nour","courriel":"nour@example.tn",
                                 "motDePasse":"court","code":"ZM-4712"}"""))
                .andExpect(status().isBadRequest());

        // Aller demander a Keycloak de refuser ce qu'on sait deja invalide, c'est
        // un aller-retour reseau et une ligne dans son journal de securite.
        then(identite).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("refuse une adresse malformee")
    void courrielMalformeRefuse() throws Exception {
        mockMvc.perform(post("/bff/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Nour","courriel":"pas-une-adresse",
                                 "motDePasse":"ruche-sans-fin-2026","code":"ZM-4712"}"""))
                .andExpect(status().isBadRequest());

        then(identite).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("cree le compte et repond 201, sans corps")
    void inscriptionReussie() throws Exception {
        mockMvc.perform(post("/bff/inscription")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Nour Ben Salah","courriel":"nour@example.tn",
                                 "motDePasse":"ruche-sans-fin-2026","code":"ZM-4712"}"""))
                .andExpect(status().isCreated());

        then(identite).should()
                .inscription("Nour Ben Salah", "nour@example.tn", "ruche-sans-fin-2026", "ZM-4712");
        then(decodeur).should(org.mockito.Mockito.never()).decode(any());
    }
}
