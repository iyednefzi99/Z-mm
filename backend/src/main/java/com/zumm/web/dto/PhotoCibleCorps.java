package com.zumm.web.dto;

import com.zumm.domain.Photo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Attache une photo a l'un des cinq objets possibles (SPRINT-21).
 *
 * <p>Complete {@link PhotoCorps}, qui reste le corps de la route historique
 * {@code POST /api/visites/{id}/photos} : la cible y est portee par le chemin,
 * ici par le corps.
 *
 * <p>La restriction d'URL est la meme, et pour la meme raison qu'au SPRINT-12 :
 * une chaine {@code javascript:} ou {@code data:text/html} enregistree ici
 * ressortirait telle quelle dans la PWA, et deviendrait une XSS stockee le jour
 * ou elle alimente un {@code src}. Filtrer a l'entree vaut mieux qu'esperer un
 * echappement correct a chaque sortie.
 *
 * @param cible   nature de l'objet porteur
 * @param cibleId identifiant de cet objet dans sa table
 * @param url     reference de l'image
 * @param legende legende facultative
 */
public record PhotoCibleCorps(
        @NotNull Photo.Cible cible,
        @NotNull Long cibleId,
        @NotBlank
        @Size(max = 500)
        @Pattern(
                regexp = "^(https?://[^\\s\"'<>\\\\]+|/[^\\s\"'<>\\\\]*)$",
                message = "L'URL doit etre http(s) ou un chemin relatif du stockage.")
        String url,
        @Size(max = 200) String legende) {
}
