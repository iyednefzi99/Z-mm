package com.zumm.web.dto;

import com.zumm.domain.PartageTelemetrie;
import java.time.Instant;

/**
 * Un partage tel qu'il se lit dans la liste (SPRINT-26).
 *
 * <p>{@code url} n'est renseignee QU'A LA CREATION : le jeton n'existe en clair
 * qu'une fois, la base n'en gardant que l'empreinte. La relire plus tard est
 * impossible — c'est le prix, assume, de ne rien stocker de reutilisable.
 */
public record PartageReponse(
        Long id,
        Long rucheId,
        String libelle,
        Instant creeLe,
        Instant expireLe,
        Instant revoqueLe,
        Instant derniereUtilisation,
        boolean actif,
        String url) {

    public static PartageReponse de(PartageTelemetrie partage, Instant maintenant) {
        return new PartageReponse(
                partage.getId(),
                partage.getRucheId(),
                partage.getLibelle(),
                partage.getCreeLe(),
                partage.getExpireLe(),
                partage.getRevoqueLe(),
                partage.getDerniereUtilisation(),
                partage.utilisable(maintenant),
                null);
    }

    /** Vue de la creation, avec l'URL a transmettre — la seule fois ou elle existe. */
    public PartageReponse avecUrl(String url) {
        return new PartageReponse(id, rucheId, libelle, creeLe, expireLe, revoqueLe,
                derniereUtilisation, actif, url);
    }
}
