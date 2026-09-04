package com.zumm.web.dto;

import com.zumm.domain.RessourceFlorale;

/** Vue exposee d'une ressource florale declaree (SPRINT-21). */
public record RessourceFloraleReponse(
        Long id,
        String ressource,
        Integer distanceM,
        Integer moisDebut,
        Integer moisFin,
        String note) {

    public static RessourceFloraleReponse de(RessourceFlorale ressource) {
        return new RessourceFloraleReponse(
                ressource.getId(),
                ressource.getRessource(),
                ressource.getDistanceM(),
                ressource.getMoisDebut(),
                ressource.getMoisFin(),
                ressource.getNote());
    }
}
