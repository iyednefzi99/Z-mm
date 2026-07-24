package com.zumm.web.dto;

import com.zumm.domain.Photo;
import java.time.Instant;

/** Vue exposee d'une photo d'inspection (US-010/028). */
public record PhotoReponse(Long id, String url, String legende, Instant creeLe) {

    public static PhotoReponse de(Photo photo) {
        return new PhotoReponse(photo.getId(), photo.getUrl(), photo.getLegende(), photo.getCreeLe());
    }
}
