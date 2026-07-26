package com.zumm.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un point de courbe journaliere (SPRINT-18).
 *
 * <p>Porte la moyenne du jour, mais aussi le MINIMUM et le MAXIMUM : sur une
 * ruche, l'amplitude d'une journee est une information a part entiere — une chute
 * nocturne de poids ne se lit pas sur une moyenne. Les rendre ici evite d'avoir a
 * redemander la serie brute pour les obtenir.
 *
 * @param jour    debut du compartiment journalier
 * @param moyenne moyenne des releves du jour
 * @param minimum plus basse valeur du jour
 * @param maximum plus haute valeur du jour
 * @param nombre  nombre de releves agreges
 */
public record PointJournalier(Instant jour, BigDecimal moyenne, BigDecimal minimum,
        BigDecimal maximum, long nombre) {
}
