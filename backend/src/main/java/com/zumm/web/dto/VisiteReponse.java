package com.zumm.web.dto;

import com.zumm.domain.EffectifQualitatif;
import com.zumm.domain.EtatSante;
import com.zumm.domain.Photo;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Visite;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Vue exposee d'une visite et de son rapport (US-009), photos comprises. */
public record VisiteReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        Long agentId,
        String agentNom,
        Long planningId,
        LocalDate dateVisite,
        LocalTime heureVisite,
        Integer dureeMin,
        RaisonVisite raison,
        String constatations,
        String actionsPrevues,
        String actionsEffectuees,
        String recommandations,
        EffectifQualitatif effectifQualitatif,
        EtatSante etatSante,
        Integer productivite,
        List<PhotoReponse> photos,
        Instant creeLe,
        Instant majLe) {

    public static VisiteReponse de(Visite v, List<Photo> photos) {
        return new VisiteReponse(
                v.getId(),
                v.getRuche().getId(),
                v.getRuche().getModele(),
                v.getAgent().getId(),
                v.getAgent().getNom(),
                v.getPlanning() == null ? null : v.getPlanning().getId(),
                v.getDateVisite(),
                v.getHeureVisite(),
                v.getDureeMin(),
                v.getRaison(),
                v.getConstatations(),
                v.getActionsPrevues(),
                v.getActionsEffectuees(),
                v.getRecommandations(),
                v.getEffectifQualitatif(),
                v.getEtatSante(),
                v.getProductivite(),
                photos.stream().map(PhotoReponse::de).toList(),
                v.getCreeLe(),
                v.getMajLe());
    }
}
