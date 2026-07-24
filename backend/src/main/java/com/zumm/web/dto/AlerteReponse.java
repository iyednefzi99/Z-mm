package com.zumm.web.dto;

import com.zumm.domain.Alerte;
import com.zumm.domain.TypeIndicateur;
import java.math.BigDecimal;
import java.time.Instant;

/** Vue exposee d'une alerte de seuil (US-018). */
public record AlerteReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        TypeIndicateur typeIndicateur,
        String niveau,
        String message,
        BigDecimal valeurDeclenchement,
        boolean ouverte,
        Instant ouverteLe,
        Instant fermeeLe) {

    public static AlerteReponse de(Alerte a) {
        return new AlerteReponse(
                a.getId(),
                a.getRuche().getId(),
                a.getRuche().getModele(),
                a.getTypeIndicateur(),
                a.getNiveau(),
                a.getMessage(),
                a.getValeurDeclenchement(),
                a.isOuverte(),
                a.getOuverteLe(),
                a.getFermeeLe());
    }
}
