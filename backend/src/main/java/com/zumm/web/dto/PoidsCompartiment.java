package com.zumm.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Repartition du poids d'une ruche entre ses compartiments (SPRINT-26, lot F1).
 *
 * <p>{@code valeur} et {@code instant} sont NULS tant qu'aucune pesee n'a eu
 * lieu sur ce compartiment — et ils le restent, plutot que de valoir zero. Une
 * hausse jamais pesee n'est pas une hausse vide : c'est une hausse inconnue, et
 * afficher 0 kg ferait croire a une ruche qui a perdu ses reserves.
 *
 * @param compartimentId identifiant du compartiment
 * @param type           corps | hausse
 * @param nbCadres       ce qu'il contient, pour situer le poids
 * @param valeur         dernier poids connu, en kilogrammes, ou {@code null}
 * @param instant        moment de cette pesee, ou {@code null}
 */
public record PoidsCompartiment(
        Long compartimentId,
        String type,
        int nbCadres,
        BigDecimal valeur,
        Instant instant) {
}
