package com.zumm.web.dto;

import com.zumm.domain.ProduitTraitement;

/**
 * Un produit du referentiel de traitement, tel que l'API l'expose (SPRINT-28).
 *
 * <p>Il pre-remplit un formulaire ; il ne fait pas autorite. {@code mention}
 * accompagne donc toujours le produit et doit etre affichee a la saisie : c'est
 * elle qui renvoie a la notice, laquelle reste la reference.
 *
 * @param haussesRetirees la contrainte reelle de la plupart des varroacides. Un
 *                        delai de carence de zero jour, lu seul, se comprend
 *                        comme « on peut recolter » — ce qui est faux tant que
 *                        les hausses sont en place
 */
public record ProduitReferentiel(
        String code,
        String nom,
        String substanceActive,
        String cible,
        String forme,
        Integer delaiCarenceJours,
        boolean haussesRetirees,
        boolean ordonnanceRequise,
        String mention) {

    public static ProduitReferentiel de(ProduitTraitement p) {
        return new ProduitReferentiel(p.getCode(), p.getNom(), p.getSubstanceActive(),
                p.getCible(), p.getForme(), p.getDelaiCarenceJours(), p.isHaussesRetirees(),
                p.isOrdonnanceRequise(), p.getMention());
    }
}
