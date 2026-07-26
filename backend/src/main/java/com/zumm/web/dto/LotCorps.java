package com.zumm.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Corps de requete d'un lot de conditionnement (US-056).
 *
 * <p>La composition est OBLIGATOIRE et non vide : un lot sans origine ne peut pas
 * etre etiquete conformement a la directive (UE) 2024/1438. Autoriser sa creation
 * « pour completer plus tard » produirait exactement le lot non conforme que ce
 * modele existe pour empecher.
 */
public record LotCorps(
        @NotNull @Size(max = 40) String reference,
        @NotNull LocalDate dateConditionnement,
        @NotNull @Positive BigDecimal quantiteKg,
        @Size(max = 60) String typeMiel,
        String note,
        @NotEmpty @Valid List<OrigineDeclaree> origines) {
}
