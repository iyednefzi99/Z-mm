package com.zumm.web.dto;

import com.zumm.domain.EffectifQualitatif;
import com.zumm.domain.EtatSante;
import com.zumm.domain.RaisonVisite;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Corps de requete pour realiser une visite et remplir son rapport (US-009).
 *
 * <p>Les trois derniers champs datent du SPRINT-20 et sont tous facultatifs :
 * un client ecrit avant eux continue de fonctionner sans changement. Ils sont
 * <strong>imbriques</strong> plutot qu'aplatis — voir {@link ObservationVisite}
 * pour le raisonnement.
 *
 * @param observation grille d'inspection structuree, ou {@code null}
 * @param meteo       releve fige au moment de la visite, ou {@code null}
 * @param pathologies maladies et ravageurs nommes ; liste vide ou {@code null}
 *                    si rien n'a ete constate
 * @param points      releves du carnet parametrable (SPRINT-28) : uniquement
 *                    les points REGARDES. Ne pas envoyer un point n'est pas
 *                    l'envoyer a « non »
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
        @Min(1) @Max(3) Integer productivite,
        @Valid ObservationVisite observation,
        @Valid MeteoVisite meteo,
        @Valid List<PathologieCorps> pathologies,
        @Valid List<PointReleve> points) {

    /** Les pathologies, jamais {@code null} : evite une garde a chaque appelant. */
    public List<PathologieCorps> pathologiesOuVide() {
        return pathologies == null ? List.of() : pathologies;
    }

    /** Les points du carnet, jamais {@code null}. */
    public List<PointReleve> pointsOuVide() {
        return points == null ? List.of() : points;
    }
}
