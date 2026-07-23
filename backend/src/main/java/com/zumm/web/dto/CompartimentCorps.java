package com.zumm.web.dto;

import com.zumm.domain.TypeCompartiment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Un compartiment dans la composition d'une ruche (US-004).
 *
 * @param type     corps ou hausse
 * @param nbCadres nombre de cadres, de 1 a 10
 */
public record CompartimentCorps(
        @NotNull TypeCompartiment type,
        @Min(1) @Max(10) int nbCadres) {
}
