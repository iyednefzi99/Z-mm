package com.zumm.web.dto;

import com.zumm.domain.Fermier;
import java.time.Instant;

/**
 * Vue exposee d'un fermier (US-001). Le {@code tenant_id} n'est jamais expose :
 * il est implicite au contexte d'authentification.
 */
public record FermierReponse(
        Long id,
        String nom,
        String contact,
        Instant creeLe,
        Instant majLe) {

    public static FermierReponse de(Fermier fermier) {
        return new FermierReponse(
                fermier.getId(),
                fermier.getNom(),
                fermier.getContact(),
                fermier.getCreeLe(),
                fermier.getMajLe());
    }
}
