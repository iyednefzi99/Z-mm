package com.zumm.web.dto;

import com.zumm.domain.SuiviReine;
import java.time.Instant;
import java.time.LocalDate;

/** Vue exposee d'un evenement de reine (US-032). */
public record ReineReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        LocalDate dateEvenement,
        String statut,
        String couleurMarquage,
        Integer anneeNaissance,
        String race,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static ReineReponse de(SuiviReine r) {
        return new ReineReponse(
                r.getId(),
                r.getRuche().getId(),
                r.getRuche().getModele(),
                r.getDateEvenement(),
                r.getStatut(),
                r.getCouleurMarquage(),
                r.getAnneeNaissance(),
                r.getRace(),
                r.getNote(),
                r.getCreeLe(),
                r.getMajLe());
    }
}
