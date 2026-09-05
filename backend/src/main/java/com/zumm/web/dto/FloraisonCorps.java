package com.zumm.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Corps de requete pour une floraison observee (SPRINT-32, lot H).
 *
 * <p>Seuls la ressource, l'annee et la date de debut sont exiges. Le pic et la
 * fin se completent plus tard, parfois des semaines apres : les demander a la
 * saisie obligerait a attendre la fin de la miellee pour noter qu'elle a
 * commence.
 *
 * @param abondance 0 (nulle) a 3 (exceptionnelle) — une echelle percue, jamais
 *                  une mesure : personne ne pese le nectar d'une parcelle
 */
public record FloraisonCorps(
        @NotNull Long ressourceId,
        @NotNull @Min(1990) @Max(2100) Integer annee,
        @NotNull LocalDate dateDebut,
        LocalDate datePic,
        LocalDate dateFin,
        @Min(0) @Max(3) Integer abondance,
        String note) {
}
