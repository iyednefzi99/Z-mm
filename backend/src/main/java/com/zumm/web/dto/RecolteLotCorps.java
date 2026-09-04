package com.zumm.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.constraints.Size;

/**
 * Une recolte enregistree sur tout un rucher (SPRINT-23, lot B).
 *
 * <p>Repond a la ligne « recolte sur tout un rucher en une saisie » du §6.
 *
 * <p><strong>La quantite est PAR RUCHE, jamais un total a repartir.</strong>
 * Diviser une masse totale par le nombre de colonies fabriquerait une donnee
 * fausse pour chacune — et c'est precisement cette donnee qui alimente la
 * production par ruche, la correlation meteo et la comparaison d'emplacements.
 * Une balance donne un total ; le registre, lui, demande ce que chaque colonie a
 * produit.
 *
 * @param quantiteKgParRuche masse recoltee sur CHAQUE ruche visee
 * @param forcerCarence      passer outre une carence, avec motif obligatoire ;
 *                           s'applique a chaque ruche concernee du lot
 */
public record RecolteLotCorps(
        @NotNull @Valid CibleLot cible,
        @NotNull LocalDate dateRecolte,
        @NotNull @PositiveOrZero BigDecimal quantiteKgParRuche,
        @Size(max = 60) String typeMiel,
        String note,
        boolean forcerCarence,
        @Size(max = 2000) String motifForcage) {
}
