package com.zumm.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Corps de requete pour enregistrer un evenement de reine (US-032).
 *
 * @param rucheId         ruche concernee, obligatoire
 * @param dateEvenement   date de l'evenement, obligatoire
 * @param statut          introduite / en_ponte / remplacee / disparue / essaimee
 * @param couleurMarquage code international (blanc/jaune/rouge/vert/bleu), optionnel
 */
public record ReineCorps(
        @NotNull Long rucheId,
        @NotNull LocalDate dateEvenement,
        @NotNull @Pattern(regexp = "introduite|en_ponte|remplacee|disparue|essaimee") String statut,
        @Pattern(regexp = "blanc|jaune|rouge|vert|bleu") String couleurMarquage,
        Integer anneeNaissance,
        @Size(max = 60) String race,
        String note) {
}
