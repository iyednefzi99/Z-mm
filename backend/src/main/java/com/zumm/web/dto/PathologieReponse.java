package com.zumm.web.dto;

import com.zumm.domain.ObservationPathologie;

/** Vue exposee d'une pathologie constatee en visite (SPRINT-20). */
public record PathologieReponse(
        Long id,
        String pathologie,
        String gravite,
        String note) {

    public static PathologieReponse de(ObservationPathologie o) {
        return new PathologieReponse(o.getId(), o.getPathologie(), o.getGravite(), o.getNote());
    }
}
