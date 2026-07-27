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
public record ZummProperties(String nom, String version, java.util.List<String> languesActives) {
}
