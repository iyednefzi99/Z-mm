package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corps de requete pour attacher une photo a une visite (US-010/028).
 *
 * <p>L'URL est restreinte aux schemas {@code http}, {@code https} et aux chemins
 * relatifs du stockage applicatif (SPRINT-12). Sans cette restriction, une chaine
 * {@code javascript:...} ou {@code data:text/html;...} enregistree ici ressortait
 * telle quelle dans la PWA : le jour ou elle alimente un {@code href} ou un
 * {@code src}, c'est une XSS stockee, cheminant par une API pourtant authentifiee.
 * Filtrer a l'ENTREE vaut mieux qu'esperer un echappement correct a chaque sortie.
 *
 * @param url     reference de l'image, obligatoire
 * @param legende legende facultative
 */
public record PhotoCorps(
        @NotBlank
        @Size(max = 500)
        @Pattern(
                regexp = "^(https?://[^\\s\"'<>\\\\]+|/[^\\s\"'<>\\\\]*)$",
                message = "L'URL doit etre http(s) ou un chemin relatif du stockage.")
        String url,
        @Size(max = 200) String legende) {
}
