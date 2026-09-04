package com.zumm.web.dto;

import com.zumm.domain.Photo;
import java.time.Instant;

/**
 * Vue exposee d'une photo (US-010/028, elargie au SPRINT-21).
 *
 * <p>{@code cible} et {@code cibleId} disent a quoi la photo est attachee. Sans
 * eux, une galerie ne saurait pas d'ou vient une image des lors qu'elle n'est
 * plus forcement une photo de visite.
 */
public record PhotoReponse(
        Long id,
        Photo.Cible cible,
        Long cibleId,
        String url,
        String legende,
        Instant creeLe) {

    public static PhotoReponse de(Photo photo) {
        return new PhotoReponse(photo.getId(), photo.cible(), photo.cibleId(),
                photo.getUrl(), photo.getLegende(), photo.getCreeLe());
    }
}
