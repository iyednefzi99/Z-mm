package com.zumm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Le validateur d'audience est la moitie serveur du contrat porte par le mapper
 * {@code oidc-audience-mapper} du royaume Keycloak. Ces tests fixent les trois cas
 * qui comptent : bonne audience, audience etrangere, aucune audience.
 */
class ValidateurAudienceTest {

    private final ValidateurAudience validateur = new ValidateurAudience("zumm-backend");

    private static Jwt jetonAvecAudience(List<String> audiences) {
        Jwt.Builder builder = Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("sub", "utilisateur");
        if (audiences != null) {
            builder.audience(audiences);
        }
        return builder.build();
    }

    @Test
    @DisplayName("accepte un jeton emis pour l'API")
    void accepteLaBonneAudience() {
        assertThat(validateur.validate(jetonAvecAudience(List.of("zumm-backend"))).hasErrors())
                .isFalse();
    }

    @Test
    @DisplayName("accepte un jeton portant plusieurs audiences dont celle de l'API")
    void accepteUneAudienceParmiPlusieurs() {
        assertThat(validateur.validate(jetonAvecAudience(List.of("account", "zumm-backend")))
                .hasErrors())
                .isFalse();
    }

    @Test
    @DisplayName("refuse un jeton emis pour un autre client du meme royaume")
    void refuseUneAudienceEtrangere() {
        // Le cas reel : Keycloak livre par defaut un client `account` a tout
        // royaume. Un jeton obtenu pour lui a le bon emetteur et une signature
        // valide — seule l'audience le distingue d'un jeton legitime.
        var resultat = validateur.validate(jetonAvecAudience(List.of("account")));
        assertThat(resultat.hasErrors()).isTrue();
        assertThat(resultat.getErrors()).first()
                .extracting(erreur -> erreur.getErrorCode())
                .isEqualTo("invalid_token");
    }

    @Test
    @DisplayName("refuse un jeton sans audience")
    void refuseUneAudienceAbsente() {
        assertThat(validateur.validate(jetonAvecAudience(null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("ne depend d'aucun autre claim que l'audience")
    void ignoreLesAutresClaims() {
        Jwt jeton = Jwt.withTokenValue("jeton-de-test")
                .header("alg", "RS256")
                .claims(claims -> claims.putAll(Map.of(
                        "sub", "utilisateur",
                        "tenant_id", "exploitation-demo",
                        "realm_access", Map.of("roles", List.of("admin")))))
                .audience(List.of("zumm-backend"))
                .build();
        assertThat(validateur.validate(jeton).hasErrors()).isFalse();
    }
}
