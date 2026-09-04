package com.zumm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres metier exposes a l'application.
 *
 * <p>Identite du produit et locales servies, alimentees par {@code application.yml} :
 * elles suivent la version du binaire. La configuration d'EXPLOITATION, elle, vit
 * dans {@code ConfigZumm.ini} et passe par {@link com.zumm.configmetier.ConfigurationMetier},
 * qui la relit a chaud (US-025).
 *
 * @param nom            nom du produit affiche par l'API
 * @param version        version fonctionnelle courante
 * @param languesActives locales supportees, la premiere etant la langue source
 */
@ConfigurationProperties(prefix = "zumm")
public record ZummProperties(String nom, String version, java.util.List<String> languesActives,
        Auth auth) {

    /**
     * Ce que le fournisseur d'identite expose, et que l'application se contente
     * de relayer (SPRINT-25, lot J).
     *
     * @param reinitialisationUrl parcours « mot de passe oublie » du fournisseur
     *                            d'identite. VIDE par defaut, et c'est le point :
     *                            le realm livre ne configure aucun serveur
     *                            d'envoi, donc aucun courriel ne partirait.
     *                            Afficher le lien quand meme produirait le pire
     *                            des trois etats — un utilisateur qui attend un
     *                            message qui n'arrive jamais. L'exploitant qui a
     *                            configure son SMTP renseigne cette URL, et le
     *                            lien apparait.
     */
    public record Auth(String reinitialisationUrl) {
    }

    /** Jamais nul : un `auth` absent du fichier ne doit pas casser `/api/info`. */
    public ZummProperties {
        auth = auth == null ? new Auth("") : auth;
    }
}
