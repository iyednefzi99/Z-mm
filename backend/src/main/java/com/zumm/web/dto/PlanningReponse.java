package com.zumm.web.dto;

import com.zumm.domain.Planning;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.StatutPlanning;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Vue exposee d'un planning de visite (US-007/008). */
public record PlanningReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        Long agentId,
        String agentNom,
        Long superviseurId,
        String superviseurNom,
        LocalDate datePrevue,
        LocalTime heurePrevue,
        Integer dureeMin,
        RaisonVisite raison,
        StatutPlanning statut,
        String motifRefus,
        Instant creeLe,
        Instant majLe) {

    public static PlanningReponse de(Planning p) {
        var sup = p.getSuperviseur();
        return new PlanningReponse(
                p.getId(),
                p.getRuche().getId(),
                p.getRuche().getModele(),
                p.getAgent().getId(),
                p.getAgent().getNom(),
                sup == null ? null : sup.getId(),
                sup == null ? null : sup.getNom(),
                p.getDatePrevue(),
                p.getHeurePrevue(),
                p.getDureeMin(),
                p.getRaison(),
                p.getStatut(),
                p.getMotifRefus(),
                p.getCreeLe(),
                p.getMajLe());
    }
}
