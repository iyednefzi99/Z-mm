package com.zumm.web.dto;

import com.zumm.domain.EffectifQualitatif;
import com.zumm.domain.EtatSante;
import com.zumm.domain.RaisonVisite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Corps de requete pour realiser une visite et remplir son rapport (US-009).
 */
public record VisiteCorps(
        @NotNull Long rucheId,
        @NotNull Long agentId,
        Long planningId,
        @NotNull LocalDate dateVisite,
        LocalTime heureVisite,
        @Positive Integer dureeMin,
        RaisonVisite raison,
        String constatations,
        String actionsPrevues,
        String actionsEffectuees,
        String recommandations,
        EffectifQualitatif effectifQualitatif,
        EtatSante etatSante,
        @Min(1) @Max(3) Integer productivite) {
}
