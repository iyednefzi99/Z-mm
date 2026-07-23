package com.zumm.web.dto;

import com.zumm.domain.Compartiment;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeCompartiment;
import java.time.Instant;
import java.util.List;

/**
 * Vue exposee d'une ruche et de sa composition (US-004).
 */
public record RucheReponse(
        Long id,
        String modele,
        Long siteId,
        String siteNom,
        Long fermeId,
        String fermeNom,
        Long agentResponsableId,
        String agentResponsableNom,
        EtatRuche etat,
        int nbHausses,
        List<CompartimentReponse> compartiments,
        Instant creeLe,
        Instant majLe) {

    /** Un compartiment tel qu'expose. */
    public record CompartimentReponse(Long id, TypeCompartiment type, int nbCadres) {
        static CompartimentReponse de(Compartiment c) {
            return new CompartimentReponse(c.getId(), c.getType(), c.getNbCadres());
        }
    }

    public static RucheReponse de(Ruche ruche) {
        var agent = ruche.getAgentResponsable();
        List<CompartimentReponse> composition = ruche.getCompartiments().stream()
                .map(CompartimentReponse::de)
                .toList();
        long hausses = ruche.getCompartiments().stream()
                .filter(c -> c.getType() == TypeCompartiment.HAUSSE)
                .count();
        return new RucheReponse(
                ruche.getId(),
                ruche.getModele(),
                ruche.getSite().getId(),
                ruche.getSite().getNom(),
                ruche.getFerme().getId(),
                ruche.getFerme().getNom(),
                agent == null ? null : agent.getId(),
                agent == null ? null : agent.getNom(),
                ruche.getEtat(),
                (int) hausses,
                composition,
                ruche.getCreeLe(),
                ruche.getMajLe());
    }
}
