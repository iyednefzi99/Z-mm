package com.zumm.web.dto;

import com.zumm.domain.Recolte;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'une recolte (US-033). {@code qrPayload} est la donnee a encoder
 * dans le QR code cote client : la reference de tracabilite du lot.
 */
public record RecolteReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        LocalDate dateRecolte,
        BigDecimal quantiteKg,
        String typeMiel,
        String lot,
        String note,
        String qrPayload,
        Instant creeLe,
        Instant majLe) {

    public static RecolteReponse de(Recolte r) {
        return new RecolteReponse(
                r.getId(),
                r.getRuche().getId(),
                r.getRuche().getModele(),
                r.getDateRecolte(),
                r.getQuantiteKg(),
                r.getTypeMiel(),
                r.getLot(),
                r.getNote(),
                "zumm:tracabilite:" + r.getLot(),
                r.getCreeLe(),
                r.getMajLe());
    }
}
