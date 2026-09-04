package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Correlation entre un indicateur meteo et la production (SPRINT-22).
 *
 * @param indicateur     temperature | humidite | vent
 * @param coefficient    Pearson, de -1 a 1. {@code null} quand il n'existe pas :
 *                       echantillon d'une seule paire, ou serie constante. Rendre
 *                       0 laisserait croire a une absence de lien MESUREE
 * @param echantillon    nombre de paires (visite, production du mois suivant)
 * @param interpretation code de lecture : echantillon_insuffisant, variance_nulle,
 *                       lien_faible, lien_modere_positif, lien_marque_negatif...
 *                       Le seuil d'echantillon prime sur la force du lien : sur
 *                       huit paires, « lien marque » serait une affirmation
 *                       gratuite, et c'est celle qu'un tableau de bord fait retenir
 */
public record CorrelationMeteo(
        String indicateur,
        BigDecimal coefficient,
        int echantillon,
        String interpretation) {
}
