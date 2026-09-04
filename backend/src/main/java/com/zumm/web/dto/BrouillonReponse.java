package com.zumm.web.dto;

import com.zumm.domain.BrouillonVisite;
import java.time.Instant;

/**
 * Un brouillon rendu a l'ecran de reprise (SPRINT-24).
 *
 * <p>{@code majLe} et {@code appareil} sont ce qui rend la reprise decidable :
 * « commence il y a deux heures sur le telephone » se reprend, « commence il y a
 * trois semaines » s'efface.
 */
public record BrouillonReponse(
        Long id,
        Long agentId,
        String agentNom,
        Long rucheId,
        String rucheModele,
        String siteNom,
        String contenu,
        String appareil,
        Instant creeLe,
        Instant majLe) {

    public static BrouillonReponse de(BrouillonVisite b) {
        return new BrouillonReponse(
                b.getId(),
                b.getAgent().getId(),
                b.getAgent().getNom(),
                b.getRuche().getId(),
                b.getRuche().getModele(),
                b.getRuche().getSite() == null ? null : b.getRuche().getSite().getNom(),
                b.getContenu(),
                b.getAppareil(),
                b.getCreeLe(),
                b.getMajLe());
    }
}
