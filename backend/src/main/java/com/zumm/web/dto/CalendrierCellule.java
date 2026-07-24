package com.zumm.web.dto;

import com.zumm.domain.EtatSante;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Visite;
import java.time.LocalDate;
import java.util.List;

/**
 * Cellule du calendrier matriciel agents × ruches (US-012) : les visites d'un
 * agent sur une ruche pendant la periode retenue, avec un resume par visite pour
 * la pop-up de la console.
 */
public record CalendrierCellule(
        Long agentId,
        String agentNom,
        Long rucheId,
        String rucheModele,
        int nombreVisites,
        List<VisiteBreve> visites) {

    /** Resume d'une visite affiche au survol / clic d'une cellule. */
    public record VisiteBreve(Long id, LocalDate date, RaisonVisite raison, EtatSante etatSante) {

        public static VisiteBreve de(Visite v) {
            return new VisiteBreve(v.getId(), v.getDateVisite(), v.getRaison(), v.getEtatSante());
        }
    }
}
