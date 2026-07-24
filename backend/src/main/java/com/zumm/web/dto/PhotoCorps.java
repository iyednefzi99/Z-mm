package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de requete pour attacher une photo a une visite (US-010/028).
 *
 * @param url     reference de l'image, obligatoire
 * @param legende legende facultative
 */
public record PhotoCorps(
        @NotBlank @Size(max = 500) String url,
        @Size(max = 200) String legende) {
}
