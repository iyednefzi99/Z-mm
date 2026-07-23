package com.zumm.web.dto;

import com.zumm.domain.Agent;
import com.zumm.domain.RoleAgent;
import java.time.Instant;

/**
 * Vue exposee d'un agent (US-005). La ferme est facultative.
 */
public record AgentReponse(
        Long id,
        String nom,
        RoleAgent role,
        Long fermeId,
        String fermeNom,
        Instant creeLe,
        Instant majLe) {

    public static AgentReponse de(Agent agent) {
        var ferme = agent.getFerme();
        return new AgentReponse(
                agent.getId(),
                agent.getNom(),
                agent.getRole(),
                ferme == null ? null : ferme.getId(),
                ferme == null ? null : ferme.getNom(),
                agent.getCreeLe(),
                agent.getMajLe());
    }
}
