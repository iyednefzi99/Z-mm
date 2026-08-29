package com.zumm.web.dto;

import com.zumm.domain.Nourrissement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Vue exposee d'un nourrissement (SPRINT-20). */
public record NourrissementReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        Long agentId,
        String agentNom,
        Long visiteId,
        LocalDate dateApport,
        String typeAliment,
        BigDecimal quantite,
        String quantiteUnite,
        String motif,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static NourrissementReponse de(Nourrissement n) {
        return new NourrissementReponse(
                n.getId(),
                n.getRuche().getId(),
                n.getRuche().getModele(),
                n.getAgent().getId(),
                n.getAgent().getNom(),
                n.getVisite() == null ? null : n.getVisite().getId(),
                n.getDateApport(),
                n.getTypeAliment(),
                n.getQuantite(),
                n.getQuantiteUnite(),
                n.getMotif(),
                n.getNote(),
                n.getCreeLe(),
                n.getMajLe());
    }
}
