package com.zumm.web.dto;

import com.zumm.domain.Depense;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Une depense telle qu'elle se lit dans la liste (SPRINT-27, lot E). */
public record DepenseReponse(
        Long id,
        String libelle,
        String categorie,
        BigDecimal montantEur,
        LocalDate dateDepense,
        Long rucheId,
        String rucheModele,
        Long siteId,
        String siteNom,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static DepenseReponse de(Depense depense) {
        return new DepenseReponse(
                depense.getId(),
                depense.getLibelle(),
                depense.getCategorie(),
                depense.getMontantEur(),
                depense.getDateDepense(),
                depense.getRuche() == null ? null : depense.getRuche().getId(),
                depense.getRuche() == null ? null : depense.getRuche().getModele(),
                depense.getSite() == null ? null : depense.getSite().getId(),
                depense.getSite() == null ? null : depense.getSite().getNom(),
                depense.getNote(),
                depense.getCreeLe(),
                depense.getMajLe());
    }
}
