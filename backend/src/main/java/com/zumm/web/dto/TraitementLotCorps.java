package com.zumm.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Un traitement applique a plusieurs ruches (SPRINT-23, lot B).
 *
 * <p>Le cas d'ecole du §11 : « sur un rucher de quarante ruches, un traitement
 * se saisit quarante fois ». Le corps reprend celui d'un traitement unitaire,
 * dont le {@code rucheId} est ignore — c'est la cible qui designe les ruches.
 *
 * @param cible     ruches visees, ou rucher entier
 * @param traitement acte a appliquer ; son {@code rucheId} n'est pas lu
 */
public record TraitementLotCorps(
        @NotNull @Valid CibleLot cible,
        @NotNull @Valid TraitementCorps traitement) {
}
