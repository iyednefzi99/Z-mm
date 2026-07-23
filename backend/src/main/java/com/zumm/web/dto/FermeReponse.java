package com.zumm.web.dto;

import com.zumm.domain.Ferme;
import java.time.Instant;

/**
 * Vue exposee d'une ferme (US-002), avec le rappel du fermier proprietaire.
 */
public record FermeReponse(
        Long id,
        String nom,
        Long fermierId,
        String fermierNom,
        Instant creeLe,
        Instant majLe) {

    public static FermeReponse de(Ferme ferme) {
        return new FermeReponse(
                ferme.getId(),
                ferme.getNom(),
                ferme.getFermier().getId(),
                ferme.getFermier().getNom(),
                ferme.getCreeLe(),
                ferme.getMajLe());
    }
}
