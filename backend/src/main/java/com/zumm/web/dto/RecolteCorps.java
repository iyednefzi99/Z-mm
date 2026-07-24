package com.zumm.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corps de requete pour enregistrer une recolte (US-033). Le numero de lot est
 * genere par le serveur, pas fourni par le client.
 *
 * @param rucheId     ruche recoltee, obligatoire
 * @param dateRecolte date de la recolte, obligatoire
 * @param quantiteKg  quantite recoltee en kg, positive ou nulle
 * @param typeMiel    type de miel (toutes fleurs, acacia…), optionnel
 */
public record RecolteCorps(
        @NotNull Long rucheId,
        @NotNull LocalDate dateRecolte,
        @NotNull @PositiveOrZero BigDecimal quantiteKg,
        @Size(max = 60) String typeMiel,
        String note) {
}
