package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Lien entre une classe de couvert autour d'un rucher et la sante de ses
 * colonies (SPRINT-33).
 *
 * <p>Meme forme que {@link CorrelationMeteo}, et ce n'est pas une coincidence :
 * les deux repondent a la seule question qu'un coefficient permette de poser
 * honnetement — « y a-t-il un lien, et sur combien d'observations ? ». La taille
 * de l'echantillon voyage donc AVEC le coefficient, jamais separement.
 *
 * @param classe         classe de couvert de la taxonomie fermee de la `V31`
 * @param coefficient    Pearson arrondi au centieme, ou {@code null} quand la
 *                       formule n'a pas de sens — jamais 0, qui serait une
 *                       affirmation d'absence de lien
 * @param echantillon    nombre de RUCHERS apparies, et non de colonies : c'est
 *                       le rucher qui porte un environnement, pas la ruche
 * @param interpretation code de lecture, ou {@code echantillon_insuffisant}
 */
public record CorrelationFlore(String classe, BigDecimal coefficient, int echantillon,
        String interpretation) {
}
