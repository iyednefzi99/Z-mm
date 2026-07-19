package com.zumm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametres metier exposes a l'application.
 *
 * <p>Ces valeurs proviendront de {@code ConfigZumm.ini} (US-025, Sprint 1), relu a
 * chaud. En Sprint 0, elles sont alimentees par {@code application.yml} : l'objectif
 * de la tranche est de figer le point d'injection, pas le mecanisme de rechargement.
 *
 * @param nom            nom du produit affiche par l'API
 * @param version        version fonctionnelle courante
 * @param languesActives locales supportees, la premiere etant la langue source
 */
@ConfigurationProperties(prefix = "zumm")
public record ZummProperties(String nom, String version, java.util.List<String> languesActives) {
}
