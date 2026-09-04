package com.zumm.web.dto;

import com.zumm.domain.AbonnementCalendrier;
import java.time.Instant;

/**
 * Vue exposee d'un abonnement iCalendar (SPRINT-21).
 *
 * <p>{@link #url} n'est renseignee QU'A LA CREATION : elle contient le jeton en
 * clair, qui n'existe nulle part ailleurs — ni en base, qui n'en garde que
 * l'empreinte, ni dans les listes ulterieures. C'est le meme parti que pour une
 * cle d'API : on la montre une fois, on la recopie, et si elle est perdue on en
 * emet une autre.
 */
public record AbonnementReponse(
        Long id,
        Long agentId,
        String libelle,
        Instant creeLe,
        Instant expireLe,
        Instant revoqueLe,
        Instant derniereUtilisation,
        boolean actif,
        String url) {

    public static AbonnementReponse de(AbonnementCalendrier abonnement, Instant maintenant) {
        return new AbonnementReponse(
                abonnement.getId(),
                abonnement.getAgentId(),
                abonnement.getLibelle(),
                abonnement.getCreeLe(),
                abonnement.getExpireLe(),
                abonnement.getRevoqueLe(),
                abonnement.getDerniereUtilisation(),
                abonnement.utilisable(maintenant),
                null);
    }

    /** Vue de la creation, avec l'URL a recopier — la seule fois ou elle existe. */
    public AbonnementReponse avecUrl(String url) {
        return new AbonnementReponse(id, agentId, libelle, creeLe, expireLe, revoqueLe,
                derniereUtilisation, actif, url);
    }
}
