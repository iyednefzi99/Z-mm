package com.zumm.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Un nourrissement applique a plusieurs ruches (SPRINT-23, lot B).
 *
 * <p>Le sirop se pose rucher par rucher, jamais colonie par colonie : c'est
 * l'operation qui, en pratique, se saisit le plus souvent en lot.
 */
public record NourrissementLotCorps(
        @NotNull @Valid CibleLot cible,
        @NotNull @Valid NourrissementCorps nourrissement) {
}
