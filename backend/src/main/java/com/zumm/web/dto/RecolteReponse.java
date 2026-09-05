package com.zumm.web.dto;

import com.zumm.domain.Recolte;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'une recolte (US-033). {@code qrPayload} est la donnee a encoder
 * dans le QR code cote client : la reference de tracabilite du lot.
 *
 * <p>{@code carenceForcee} et {@code motifForcage} (SPRINT-22) disent qu'une
 * recolte a ete enregistree malgre un delai de carence en cours, et pourquoi.
 * Les taire aurait rendu le forcage invisible partout sauf au journal d'audit —
 * c'est-a-dire nulle part ou l'apiculteur regarde.
 */
public record RecolteReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        LocalDate dateRecolte,
        BigDecimal quantiteKg,
        String typeMiel,
        String typeProduit,
        String unite,
        BigDecimal humiditePct,
        String lot,
        String note,
        String qrPayload,
        boolean carenceForcee,
        String motifForcage,
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
                r.getTypeProduit(),
                r.getUnite(),
                r.getHumiditePct(),
                r.getLot(),
                r.getNote(),
                "zumm:tracabilite:" + r.getLot(),
                r.isCarenceForcee(),
                r.getMotifForcage(),
                r.getCreeLe(),
                r.getMajLe());
    }
}
