package com.zumm.web.dto;

/**
 * Corps d'une decision de superviseur sur un planning (US-008). Le motif n'est
 * utile qu'au refus.
 *
 * @param motif justification, requise au refus, ignoree a l'approbation
 */
public record DecisionCorps(String motif) {
}
