package com.zumm.web.dto;

import com.zumm.domain.RaisonVisite;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Corps de requete pour planifier une visite (US-007).
 *
 * @param rucheId        ruche a visiter (meme tenant)
 * @param agentId        agent affecte (meme tenant)
 * @param superviseurId  superviseur charge d'approuver, facultatif
 * @param datePrevue     date prevue, obligatoire
 * @param heurePrevue    heure prevue, facultative
 * @param dureeMin       duree estimee en minutes, facultative (> 0)
 * @param raison         motif ; « controle » par defaut si absent
 */
public record PlanningCorps(
        @NotNull Long rucheId,
        @NotNull Long agentId,
        Long superviseurId,
        @NotNull LocalDate datePrevue,
        LocalTime heurePrevue,
        @Positive Integer dureeMin,
        RaisonVisite raison) {
}
