package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Reponse du service {@code getZummHoneyActualQuantity} (US-026) : quantite de miel
 * actuelle, dans l'unite demandee, pour une ruche ou pour l'ensemble du rucher.
 *
 * @param rucheId  ruche concernee, ou null pour le total du rucher
 * @param quantite quantite de miel estimee
 * @param unite    unite de la quantite (kg par defaut)
 */
public record QuantiteMiel(Long rucheId, BigDecimal quantite, String unite) {
}
