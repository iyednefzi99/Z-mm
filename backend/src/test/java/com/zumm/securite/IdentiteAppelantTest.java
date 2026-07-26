package com.zumm.securite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Le point unique de lecture d'identite doit rendre la MEME chose quelle que soit
 * la chaine d'authentification.
 *
 * <p>Ce test existe a cause d'un defaut reel : {@code AuditAspect} ne lisait que le
 * {@link Jwt} et retombait sur {@code Authentication#getName()} pour les sessions
 * de navigateur — lequel rend le {@code sub}. Le journal d'audit inscrivait donc un
 * UUID pour les utilisateurs humains, et un nom d'utilisateur pour les machines.
 * C'est exactement la derive que la Javadoc d'{@link IdentiteAppelant} annonce :
 * « un jour l'un des deux serait oublie ».
 */
class IdentiteAppelantTest {

    private static final String SUJET = "9f1c2b7e-0f4a-4c39-9b6d-1e8a2c5d7f30";

    /** Jeton porteur d'une passerelle de capteurs ou d'une integration tierce. */
    private static Authentication jeton(String nomUtilisateur) {
        Jwt.Builder builder = Jwt.withTokenValue("factice")
                .header("alg", "RS256")
                .subject(SUJET)
                .claim("email", "capteur@zumm.test")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (nomUtilisateur != null) {
            builder.claim("preferred_username", nomUtilisateur);
        }
        return new JwtAuthenticationToken(builder.build(),
                List.of(new SimpleGrantedAuthority("ROLE_capteur")));
    }

    /** Session de navigateur ouverte par le BFF : le porteur est un {@code OidcUser}. */
    private static Authentication session(String nomUtilisateur) {
        Map<String, Object> claims = new java.util.HashMap<>(Map.of(
                "sub", SUJET,
                "email", "amina@zumm.test"));
        if (nomUtilisateur != null) {
            claims.put("preferred_username", nomUtilisateur);
        }
        OidcIdToken jetonIdentite = new OidcIdToken("factice", Instant.now(),
                Instant.now().plusSeconds(300), claims);
        // `sub` comme attribut de nom : c'est le defaut de Spring pour OIDC, et
        // c'est precisement ce qui rendait `getName()` inexploitable en audit.
        DefaultOidcUser utilisateur = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_apiculteur")), jetonIdentite, "sub");
        return new TestingAuthenticationToken(utilisateur, null,
                List.of(new SimpleGrantedAuthority("ROLE_apiculteur")));
    }

    @Test
    @DisplayName("Le sujet et le courriel se lisent identiquement sur les deux porteurs")
    void sujetEtEmailIdentiquesSurLesDeuxChaines() {
        IdentiteAppelant parJeton = IdentiteAppelant.de(jeton("passerelle-nord"));
        IdentiteAppelant parSession = IdentiteAppelant.de(session("amina"));

        assertThat(parJeton.sujet()).isEqualTo(SUJET);
        assertThat(parSession.sujet()).isEqualTo(SUJET);
        assertThat(parJeton.email()).isEqualTo("capteur@zumm.test");
        assertThat(parSession.email()).isEqualTo("amina@zumm.test");
    }

    @Test
    @DisplayName("Le nom d'audit est le nom d'utilisateur, pas l'UUID, sur les DEUX chaines")
    void nomDAuditIdentiqueSurLesDeuxChaines() {
        assertThat(IdentiteAppelant.de(jeton("passerelle-nord")).nomPourAudit())
                .isEqualTo("passerelle-nord");
        // Le point du test : avant le correctif, cette ligne rendait le sujet.
        assertThat(IdentiteAppelant.de(session("amina")).nomPourAudit())
                .isEqualTo("amina");
    }

    @Test
    @DisplayName("Sans nom d'utilisateur, le journal retombe sur le sujet plutot que sur rien")
    void repliSurLeSujetQuandLeNomManque() {
        assertThat(IdentiteAppelant.de(jeton(null)).nomPourAudit()).isEqualTo(SUJET);
        assertThat(IdentiteAppelant.de(session(null)).nomPourAudit()).isEqualTo(SUJET);
    }

    @Test
    @DisplayName("Hors contexte authentifie, l'acteur est « systeme » et jamais vide")
    void acteurSystemeHorsContexte() {
        assertThat(IdentiteAppelant.de(null).nomPourAudit())
                .isEqualTo(IdentiteAppelant.ACTEUR_SYSTEME);

        // Principal d'un type inconnu des deux chaines : ne doit ni lever, ni rendre null.
        Authentication inconnu = new TestingAuthenticationToken("anonyme", null, List.of());
        IdentiteAppelant identite = IdentiteAppelant.de(inconnu);
        assertThat(identite.sujet()).isNull();
        assertThat(identite.nomPourAudit()).isEqualTo(IdentiteAppelant.ACTEUR_SYSTEME);
    }
}
