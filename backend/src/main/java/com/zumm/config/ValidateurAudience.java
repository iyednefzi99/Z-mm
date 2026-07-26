package com.zumm.config;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Exige que le jeton ait ete emis POUR cette API (claim {@code aud}).
 *
 * <p>Sans cette verification, valider l'emetteur ne suffit pas : Keycloak emet des
 * jetons pour tous les clients d'un meme royaume (dont le client {@code account},
 * present par defaut). Un jeton obtenu par une application tierce du royaume
 * serait alors accepte par l'API Zumm — l'emetteur est bon, l'audience non. Le
 * royaume declare un {@code oidc-audience-mapper} vers {@code zumm-backend} ; ce
 * validateur est la moitie serveur de ce contrat.
 *
 * <p>Reference : RFC 9068 §4 (JWT profile for OAuth 2.0 access tokens), qui rend
 * la validation d'audience obligatoire cote serveur de ressources.
 */
public class ValidateurAudience implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERREUR = new OAuth2Error(
            "invalid_token",
            "Le jeton n'a pas ete emis pour cette API (audience invalide).",
            "https://datatracker.ietf.org/doc/html/rfc9068#section-4");

    private final String audienceAttendue;

    public ValidateurAudience(String audienceAttendue) {
        this.audienceAttendue = audienceAttendue;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jeton) {
        List<String> audiences = jeton.getAudience();
        if (audiences != null && audiences.contains(audienceAttendue)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERREUR);
    }
}
