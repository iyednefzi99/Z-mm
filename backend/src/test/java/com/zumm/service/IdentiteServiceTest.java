package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zumm.repository.InvitationRepository;
import com.zumm.service.IdentiteService.Echec;
import com.zumm.service.IdentiteService.EchecIdentite;
import com.zumm.service.IdentiteService.Jetons;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Dialogue avec Keycloak (lot 1 du plan de couverture).
 *
 * <p><strong>Cette classe etait couverte a 6,3 %</strong> — la plus basse du
 * depot, sur le chemin que prend chaque connexion. Ce qu'elle contient n'est
 * pourtant pas du calcul : c'est une TRADUCTION, du statut HTTP rendu par le
 * fournisseur vers le motif que la PWA affiche. Un 401 traduit en
 * « indisponible » ferait croire a une panne la ou le mot de passe est faux ;
 * un 403 traduit en « identifiants invalides » enverrait un utilisateur suspendu
 * retaper son mot de passe indefiniment.
 *
 * <p>La table des cas est donc le coeur du test, et elle porte aussi une
 * propriete de SECURITE : compte inexistant et mot de passe faux doivent rendre
 * le meme motif, faute de quoi le formulaire devient un annuaire des comptes
 * existants.
 */
class IdentiteServiceTest {

    private static final String ROYAUME = "http://localhost:8081/realms/zumm";
    private static final String JETON = ROYAUME + "/protocol/openid-connect/token";
    private static final String UTILISATEURS =
            "http://localhost:8081/admin/realms/zumm/users";

    private static final String JETONS_OK = """
            {"access_token":"jeton-acces","id_token":"jeton-identite",
             "refresh_token":"jeton-rafraichissement","expires_in":300}""";

    private MockRestServiceServer serveur;
    private InvitationRepository invitations;
    private IdentiteService service;

    @BeforeEach
    void monter() {
        RestClient.Builder constructeur = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(constructeur).ignoreExpectOrder(true).build();
        invitations = mock(InvitationRepository.class);
        service = new IdentiteService(constructeur, invitations, ROYAUME, "zumm-bff", "secret");
    }

    // ── Connexion ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("echange identifiant et mot de passe contre des jetons")
    void connexionReussie() {
        serveur.expect(requestTo(JETON))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(JETONS_OK, MediaType.APPLICATION_JSON));

        Jetons jetons = service.connexion("agent@zumm.test", "motdepasse");

        assertThat(jetons.jetonAcces()).isEqualTo("jeton-acces");
        assertThat(jetons.jetonIdentite()).isEqualTo("jeton-identite");
        assertThat(jetons.jetonRafraichissement()).isEqualTo("jeton-rafraichissement");
        assertThat(jetons.dureeSecondes()).isEqualTo(300L);
        serveur.verify();
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("chaque statut du fournisseur tombe sur le bon motif")
    @CsvSource({
        "400, IDENTIFIANTS_INVALIDES",
        "401, IDENTIFIANTS_INVALIDES",
        "404, IDENTIFIANTS_INVALIDES",
        "403, COMPTE_SUSPENDU",
        "500, INDISPONIBLE",
        "503, INDISPONIBLE",
    })
    void traductionDesStatuts(int statut, Echec attendu) {
        serveur.expect(requestTo(JETON)).andRespond(withStatus(HttpStatus.valueOf(statut)));

        assertThatThrownBy(() -> service.connexion("agent@zumm.test", "motdepasse"))
                .isInstanceOf(EchecIdentite.class)
                .extracting(e -> ((EchecIdentite) e).motif())
                .isEqualTo(attendu);
    }

    @Test
    @DisplayName("un compte inexistant et un mot de passe faux sont indiscernables")
    void memeMotifPourInconnuEtMauvaisMotDePasse() {
        // Keycloak rend 401 dans les deux cas ; la propriete a tenir est que
        // Zumm n'introduise pas de difference. Le jour ou une branche
        // distinguerait les deux, ce test tomberait — et c'est exactement son
        // objet : le formulaire ne doit pas devenir un annuaire des comptes.
        serveur.expect(requestTo(JETON)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        Echec inconnu = motifDe(() -> service.connexion("inconnu@zumm.test", "x"));

        monter();
        serveur.expect(requestTo(JETON)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        Echec mauvaisMdp = motifDe(() -> service.connexion("agent@zumm.test", "faux"));

        assertThat(inconnu).isEqualTo(mauvaisMdp).isEqualTo(Echec.IDENTIFIANTS_INVALIDES);
    }

    @Test
    @DisplayName("une reponse 200 sans jeton d'acces est une indisponibilite, pas un refus")
    void reponseSansJeton() {
        serveur.expect(requestTo(JETON))
                .andRespond(withSuccess("{\"token_type\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        // Retaper son mot de passe n'y changerait rien : le distinguer d'un refus
        // est la seule facon de ne pas envoyer l'utilisateur dans une boucle.
        assertThat(motifDe(() -> service.connexion("agent@zumm.test", "x")))
                .isEqualTo(Echec.INDISPONIBLE);
    }

    @Test
    @DisplayName("expires_in absent : la duree retombe sur cinq minutes")
    void dureeParDefaut() {
        serveur.expect(requestTo(JETON))
                .andRespond(withSuccess("{\"access_token\":\"a\"}", MediaType.APPLICATION_JSON));

        Jetons jetons = service.connexion("agent@zumm.test", "motdepasse");

        assertThat(jetons.dureeSecondes()).isEqualTo(300L);
        assertThat(jetons.jetonIdentite()).isNull();
        assertThat(jetons.jetonRafraichissement()).isNull();
    }

    // ── Inscription ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("reserve la place AVANT de creer le compte, et cree l'utilisateur")
    void inscriptionReussie() {
        when(invitations.reserver("CODE-42"))
                .thenReturn(Optional.of(new InvitationRepository.Invitation("ferme-1", "apiculteur")));
        serveur.expect(requestTo(JETON))
                .andRespond(withSuccess(JETONS_OK, MediaType.APPLICATION_JSON));
        serveur.expect(requestTo(UTILISATEURS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jeton-acces"))
                .andExpect(jsonPath("$.username").value("agent@zumm.test"))
                .andExpect(jsonPath("$.firstName").value("Amal"))
                .andExpect(jsonPath("$.lastName").value("Ben Salah"))
                // Rien ne prouve encore que l'adresse appartient a qui s'inscrit.
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.attributes.tenant_id[0]").value("ferme-1"))
                .andRespond(withStatus(HttpStatus.CREATED));

        service.inscription("Amal Ben Salah", "agent@zumm.test", "MotDePasse!1", "CODE-42");

        verify(invitations, never()).relacher(anyString());
        serveur.verify();
    }

    @Test
    @DisplayName("un code inconnu refuse sans jamais joindre le fournisseur")
    void codeInconnu() {
        when(invitations.reserver("FAUX")).thenReturn(Optional.empty());

        assertThat(motifDe(() -> service.inscription("Amal", "a@zumm.test", "x", "FAUX")))
                .isEqualTo(Echec.CODE_INCONNU);
        serveur.verify();
    }

    @Test
    @DisplayName("une invitation portant un role non invitable rend la place et refuse")
    void roleNonInvitable() {
        // `admin` ne s'attribue pas par code : une invitation qui le porterait
        // serait une escalade de privileges par simple partage d'un lien.
        when(invitations.reserver("CODE-ADMIN"))
                .thenReturn(Optional.of(new InvitationRepository.Invitation("ferme-1", "admin")));

        assertThat(motifDe(() -> service.inscription("Amal", "a@zumm.test", "x", "CODE-ADMIN")))
                .isEqualTo(Echec.CODE_INCONNU);
        verify(invitations).relacher("CODE-ADMIN");
        serveur.verify();
    }

    @ParameterizedTest(name = "creation {0} → {1}")
    @DisplayName("les refus de creation sont traduits, et la place est TOUJOURS rendue")
    @CsvSource({
        "409, COURRIEL_DEJA_PRIS",
        "400, MOT_DE_PASSE_REFUSE",
        "500, INDISPONIBLE",
    })
    void refusDeCreationRendLaPlace(int statut, Echec attendu) {
        when(invitations.reserver("CODE-42"))
                .thenReturn(Optional.of(new InvitationRepository.Invitation("ferme-1", "apiculteur")));
        serveur.expect(requestTo(JETON))
                .andRespond(withSuccess(JETONS_OK, MediaType.APPLICATION_JSON));
        serveur.expect(requestTo(UTILISATEURS)).andRespond(withStatus(HttpStatus.valueOf(statut)));

        assertThat(motifDe(() ->
                service.inscription("Amal", "agent@zumm.test", "x", "CODE-42")))
                .isEqualTo(attendu);

        // Sans cette restitution, une place d'invitation serait consommee a
        // chaque tentative refusee — et un mot de passe trop court epuiserait
        // l'invitation d'une exploitation.
        verify(invitations).relacher("CODE-42");
    }

    @Test
    @DisplayName("un jeton de service indisponible rend la place au lieu de la perdre")
    void jetonDeServiceIndisponible() {
        when(invitations.reserver("CODE-42"))
                .thenReturn(Optional.of(new InvitationRepository.Invitation("ferme-1", "apiculteur")));
        serveur.expect(requestTo(JETON)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(motifDe(() ->
                service.inscription("Amal", "agent@zumm.test", "x", "CODE-42")))
                .isEqualTo(Echec.INDISPONIBLE);
        verify(invitations).relacher("CODE-42");
    }

    @Test
    @DisplayName("un nom sans espace remplit le prenom et laisse le patronyme vide")
    void nomSansEspace() {
        when(invitations.reserver("CODE-42"))
                .thenReturn(Optional.of(new InvitationRepository.Invitation("ferme-1", "superviseur")));
        serveur.expect(requestTo(JETON))
                .andRespond(withSuccess(JETONS_OK, MediaType.APPLICATION_JSON));
        serveur.expect(requestTo(UTILISATEURS))
                .andExpect(jsonPath("$.firstName").value("Amal"))
                .andExpect(jsonPath("$.lastName").value(""))
                .andExpect(jsonPath("$.realmRoles[0]").value("superviseur"))
                .andRespond(withStatus(HttpStatus.CREATED));

        service.inscription("  Amal  ", "agent@zumm.test", "MotDePasse!1", "CODE-42");

        serveur.verify();
    }

    // ── Outil ───────────────────────────────────────────────────────────────

    private static Echec motifDe(Runnable action) {
        try {
            action.run();
        } catch (EchecIdentite echec) {
            return echec.motif();
        }
        throw new AssertionError("un EchecIdentite etait attendu");
    }
}
