package com.zumm.web.dto;

import com.zumm.domain.Division;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'une division (SPRINT-21).
 *
 * <p>Les libelles de la mere et de la fille accompagnent leurs identifiants : un
 * ecran de filiation qui n'afficherait que des numeros obligerait a un
 * aller-retour par ruche pour etre lisible.
 */
public record DivisionReponse(
        Long id,
        Long rucheMereId,
        String rucheMereModele,
        Long rucheFilleId,
        String rucheFilleModele,
        Long agentId,
        String agentNom,
        Long visiteId,
        LocalDate dateDivision,
        String methode,
        Integer cadresCouvain,
        Integer cadresProvisions,
        String origineReine,
        String note,
        Instant creeLe) {

    public static DivisionReponse de(Division division) {
        return new DivisionReponse(
                division.getId(),
                division.getMere().getId(),
                division.getMere().getModele(),
                division.getFille() == null ? null : division.getFille().getId(),
                division.getFille() == null ? null : division.getFille().getModele(),
                division.getAgent().getId(),
                division.getAgent().getNom(),
                division.getVisite() == null ? null : division.getVisite().getId(),
                division.getDateDivision(),
                division.getMethode(),
                division.getCadresCouvain(),
                division.getCadresProvisions(),
                division.getOrigineReine(),
                division.getNote(),
                division.getCreeLe());
    }
}
