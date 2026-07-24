package com.zumm.web.dto;

import com.zumm.domain.Mesure;
import com.zumm.domain.TypeIndicateur;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Vue exposee d'une mesure ingeree (US-017), enrichie des alertes eventuellement
 * declenchees par cette mesure (US-018).
 */
public record MesureReponse(
        Long rucheId,
        TypeIndicateur typeIndicateur,
        Instant instant,
        BigDecimal valeur,
        List<AlerteReponse> alertes) {

    public static MesureReponse de(Mesure m, List<AlerteReponse> alertes) {
        return new MesureReponse(
                m.getId().getRucheId(),
                m.getId().getTypeIndicateur(),
                m.getId().getInstant(),
                m.getValeur(),
                alertes);
    }
}
