package com.zumm.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        /**
         * miel | cire | pollen | propolis | gelee_royale | essaim | reine
         * (SPRINT-27). Absent, c'est du MIEL : c'est ce que le modele
         * presupposait avant, et le defaut preserve les donnees existantes.
         */
        @Pattern(regexp = "miel|cire|pollen|propolis|gelee_royale|essaim|reine")
        String typeProduit,
        /**
         * kg | unite. Absente, `kg`. La base refuse un essaim pese en
         * kilogrammes : cinq essaims ne pesent pas cinq kilos.
         */
        @Pattern(regexp = "kg|unite") String unite,
        /**
         * Taux d'eau du miel, en pourcentage (SPRINT-28). Facultatif : tout le
         * monde n'a pas de refractometre. Reserve au miel, entre 10 et 30 %.
         */
        @DecimalMin("10.0") @DecimalMax("30.0") BigDecimal humiditePct,
        @Size(max = 2000) String note,
        /**
         * Enregistrer malgre une carence en cours. Faux par defaut : forcer doit
         * etre un acte, jamais un reglage qu'on oublie a vrai.
         */
        boolean forcerCarence,
        /** Obligatoire des lors qu'on force. Le service refuse un forcage muet. */
        @Size(max = 2000) String motifForcage) {
}
