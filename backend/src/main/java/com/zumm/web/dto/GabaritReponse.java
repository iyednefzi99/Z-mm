package com.zumm.web.dto;

import com.zumm.domain.GabaritInspection;
import java.time.Instant;
import java.util.List;

/**
 * Vue exposee d'un gabarit d'inspection (SPRINT-28).
 *
 * @param points codes du referentiel, dans l'ordre d'affichage
 */
public record GabaritReponse(
        Long id,
        String nom,
        String description,
        boolean noyauCouvain,
        boolean noyauReine,
        boolean noyauCadres,
        boolean noyauTemperament,
        boolean parDefaut,
        boolean actif,
        List<String> points,
        Instant creeLe,
        Instant majLe) {

    public static GabaritReponse de(GabaritInspection g, List<String> points) {
        return new GabaritReponse(
                g.getId(),
                g.getNom(),
                g.getDescription(),
                g.isNoyauCouvain(),
                g.isNoyauReine(),
                g.isNoyauCadres(),
                g.isNoyauTemperament(),
                g.isParDefaut(),
                g.isActif(),
                points,
                g.getCreeLe(),
                g.getMajLe());
    }
}
