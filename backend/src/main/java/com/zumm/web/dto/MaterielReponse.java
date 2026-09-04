package com.zumm.web.dto;

import com.zumm.domain.Materiel;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un equipement, avec son echeance d'entretien CALCULEE (SPRINT-27, lot E).
 *
 * <p>{@code prochaineMaintenance} et {@code enRetard} ne sont pas stockes : les
 * ranger en base creerait une valeur a maintenir en coherence avec la derniere
 * maintenance, exactement la dette que {@code ComptageVarroaService} evite pour
 * le taux de varroa.
 */
public record MaterielReponse(
        Long id,
        String libelle,
        String categorie,
        int quantite,
        Long siteId,
        String siteNom,
        String etat,
        Integer periodiciteJours,
        LocalDate derniereMaintenance,
        LocalDate prochaineMaintenance,
        boolean enRetard,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static MaterielReponse de(Materiel materiel, LocalDate jour) {
        LocalDate prochaine = materiel.prochaineMaintenance(jour);
        return new MaterielReponse(
                materiel.getId(),
                materiel.getLibelle(),
                materiel.getCategorie(),
                materiel.getQuantite(),
                materiel.getSite() == null ? null : materiel.getSite().getId(),
                materiel.getSite() == null ? null : materiel.getSite().getNom(),
                materiel.getEtat(),
                materiel.getPeriodiciteJours(),
                materiel.getDerniereMaintenance(),
                prochaine,
                prochaine != null && !prochaine.isAfter(jour),
                materiel.getNote(),
                materiel.getCreeLe(),
                materiel.getMajLe());
    }
}
