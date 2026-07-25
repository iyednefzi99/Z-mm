package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Site voisin d'un site de reference, avec sa distance reelle (US-046, SPRINT-10).
 *
 * <p>La distance est geodesique — calculee par PostGIS sur {@code geography}, en
 * metres — et non une distance en degres, qui n'aurait pas de sens metrique.
 *
 * @param site            le site voisin
 * @param distanceMetres  distance a vol d'oiseau depuis le site de reference
 */
public record VoisinSite(SiteReponse site, BigDecimal distanceMetres) {
}
