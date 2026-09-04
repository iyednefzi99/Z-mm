package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Enregistrement d'une division (SPRINT-21).
 *
 * @param rucheMereId      colonie divisee, obligatoire
 * @param rucheFilleId     colonie issue de la division, facultative tant qu'elle
 *                         n'est pas enregistree comme ruche
 * @param agentId          qui a divise, obligatoire
 * @param visiteId         visite au cours de laquelle la division a eu lieu
 * @param dateDivision     obligatoire
 * @param methode          essaim artificiel, nucleus, partage egal...
 * @param cadresCouvain    cadres de couvain transferes a la fille
 * @param cadresProvisions cadres de provisions transferes a la fille
 * @param origineReine     ce que la fille a recu comme reine
 * @param note             precision libre
 */
public record DivisionCorps(
        @NotNull Long rucheMereId,
        Long rucheFilleId,
        @NotNull Long agentId,
        Long visiteId,
        @NotNull LocalDate dateDivision,
        @Pattern(regexp = "essaim_artificiel|nucleus|partage_egal|prelevement_cadres|autre")
        String methode,
        @Min(0) @Max(40) Integer cadresCouvain,
        @Min(0) @Max(40) Integer cadresProvisions,
        @Pattern(regexp = "cellule_royale|reine_introduite|orpheline|reine_mere|autre")
        String origineReine,
        @Size(max = 2000) String note) {
}
