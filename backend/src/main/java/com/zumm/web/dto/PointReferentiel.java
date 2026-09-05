package com.zumm.web.dto;

import com.zumm.domain.PointObservation;

/**
 * Un point du referentiel ferme, tel que l'API l'expose (SPRINT-28).
 *
 * @param typeValeur {@code booleen} (une case) ou {@code echelle} (0 a 3)
 * @param libelle    en francais, et de repli seulement : le front traduit par
 *                   {@code code}. Il est rendu tout de meme, pour qu'un client
 *                   qui rencontre un point ajoute apres sa derniere mise a jour
 *                   affiche quelque chose plutot qu'un identifiant technique
 */
public record PointReferentiel(
        String code,
        String categorie,
        String typeValeur,
        String libelle,
        Integer ordre) {

    public static PointReferentiel de(PointObservation p) {
        return new PointReferentiel(p.getCode(), p.getCategorie(), p.getTypeValeur(),
                p.getLibelle(), p.getOrdre());
    }
}
