package com.zumm.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tournee proposee a un agent pour une journee (US-047, SPRINT-10).
 *
 * <p>L'ordre est une <b>proposition</b> : il resulte d'une heuristique (plus proche
 * voisin puis 2-opt) sur des distances a vol d'oiseau. Il n'est ni optimal, ni
 * routier — l'agent reste libre de l'ignorer.
 *
 * @param agentId                identifiant de l'agent
 * @param agentNom               nom de l'agent
 * @param date                   journee concernee
 * @param nombreSites            nombre de sites a parcourir
 * @param nombreVisites          nombre total de visites planifiees
 * @param distanceTotaleMetres   longueur du chemin propose, a vol d'oiseau
 * @param etapes                 sites dans l'ordre propose
 */
public record TourneeReponse(
        Long agentId,
        String agentNom,
        LocalDate date,
        int nombreSites,
        int nombreVisites,
        BigDecimal distanceTotaleMetres,
        List<EtapeTournee> etapes) {
}
