package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Rentabilite d'une ruche sur une periode (SPRINT-27, lot E).
 *
 * <p><strong>Ce que ce DTO refuse de faire.</strong> Il ne repartit AUCUNE
 * depense non affectee. Une assurance, une formation, un vehicule ne se
 * divisent pas par le nombre de ruches : la cle de repartition serait inventee,
 * et le resultat aurait l'autorite d'un chiffre sans en avoir la matiere.
 * {@link BilanExploitation#depensesNonAffectees()} les porte a part, ou elles se
 * voient.
 *
 * <p>{@code recettesEur} valorise la production au prix du kilo de
 * {@code ConfigZumm.ini} : c'est une VALORISATION, pas un chiffre d'affaires.
 * Zumm ne sait pas a quel prix le miel a ete vendu — et ne cherche pas a le
 * savoir, la facturation etant hors perimetre (§9).
 *
 * @param productionKg production de MIEL seule : additionner des kilos de cire
 *                     et des essaims donnerait un total qui ne veut rien dire
 */
public record RentabiliteRuche(
        Long rucheId,
        String rucheModele,
        String siteNom,
        BigDecimal productionKg,
        BigDecimal recettesEur,
        BigDecimal depensesEur,
        BigDecimal resultatEur) {
}
