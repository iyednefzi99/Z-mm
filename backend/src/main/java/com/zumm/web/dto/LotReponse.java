package com.zumm.web.dto;

import com.zumm.domain.LotComposition;
import com.zumm.domain.LotConditionnement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Vue exposee d'un lot de conditionnement (US-056). */
public record LotReponse(
        Long id,
        String reference,
        LocalDate dateConditionnement,
        BigDecimal quantiteKg,
        String typeMiel,
        String note,
        List<PartReponse> composition,
        Instant creeLe,
        Instant majLe) {

    /** Part d'origine telle que stockee, avec le lot de recolte s'il existe. */
    public record PartReponse(
            Long id, Long recolteId, String recolteLot, String paysOrigine, BigDecimal pourcentage) {

        static PartReponse de(LotComposition part) {
            return new PartReponse(
                    part.getId(),
                    part.getRecolte() == null ? null : part.getRecolte().getId(),
                    part.getRecolte() == null ? null : part.getRecolte().getLot(),
                    part.getPaysOrigine(),
                    part.getPourcentage());
        }
    }

    public static LotReponse de(LotConditionnement lot) {
        return new LotReponse(
                lot.getId(),
                lot.getReference(),
                lot.getDateConditionnement(),
                lot.getQuantiteKg(),
                lot.getTypeMiel(),
                lot.getNote(),
                lot.getComposition().stream().map(PartReponse::de).toList(),
                lot.getCreeLe(),
                lot.getMajLe());
    }
}
