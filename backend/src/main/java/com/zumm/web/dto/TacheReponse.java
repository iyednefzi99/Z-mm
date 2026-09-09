package com.zumm.web.dto;

import com.zumm.domain.Tache;
import java.time.Instant;
import java.time.LocalDate;

/** Vue exposee d'une tache ou d'un rappel (US-031). */
public record TacheReponse(
        Long id,
        String libelle,
        Long rucheId,
        String rucheModele,
        Long agentId,
        String agentNom,
        LocalDate echeance,
        boolean faite,
        String priorite,
        String categorie,
        /** {@code manuelle} ou {@code regle} : une tache engendree se justifie. */
        String origine,
        /** Code de la regle qui l'a proposee, pour l'expliquer a l'ecran. */
        String regleCode,
        /** Ce que la tache consomme (SPRINT-33), ou {@code null}. */
        Long consommableId,
        String consommableLibelle,
        String consommableUnite,
        /** Combien, dans l'unite du consommable, ou {@code null} si non chiffre. */
        java.math.BigDecimal quantitePrevue,
        Instant creeLe,
        Instant majLe) {

    public static TacheReponse de(Tache t) {
        return new TacheReponse(
                t.getId(),
                t.getLibelle(),
                t.getRuche() == null ? null : t.getRuche().getId(),
                t.getRuche() == null ? null : t.getRuche().getModele(),
                t.getAgent() == null ? null : t.getAgent().getId(),
                t.getAgent() == null ? null : t.getAgent().getNom(),
                t.getEcheance(),
                t.isFaite(),
                t.getPriorite(),
                t.getCategorie(),
                t.getOrigine(),
                t.getRegleCode(),
                t.getConsommable() == null ? null : t.getConsommable().getId(),
                t.getConsommable() == null ? null : t.getConsommable().getLibelle(),
                t.getConsommable() == null ? null : t.getConsommable().getUnite(),
                t.getQuantitePrevue(),
                t.getCreeLe(),
                t.getMajLe());
    }
}
