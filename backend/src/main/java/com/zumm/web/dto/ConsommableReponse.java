package com.zumm.web.dto;

import com.zumm.domain.Consommable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un consommable et son etat de reapprovisionnement (SPRINT-27, lot E).
 *
 * <p>{@code sousSeuil} est CALCULE, et la comparaison est inclusive : arriver
 * pile au seuil, c'est deja etre a court.
 */
public record ConsommableReponse(
        Long id,
        String libelle,
        String categorie,
        BigDecimal quantite,
        String unite,
        BigDecimal seuilAlerte,
        boolean sousSeuil,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static ConsommableReponse de(Consommable consommable) {
        return new ConsommableReponse(
                consommable.getId(),
                consommable.getLibelle(),
                consommable.getCategorie(),
                consommable.getQuantite(),
                consommable.getUnite(),
                consommable.getSeuilAlerte(),
                consommable.sousSeuil(),
                consommable.getNote(),
                consommable.getCreeLe(),
                consommable.getMajLe());
    }
}
