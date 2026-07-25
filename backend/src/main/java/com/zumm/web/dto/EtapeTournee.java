package com.zumm.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Etape d'une tournee de visites (US-047, SPRINT-10) : un site, et les plannings
 * qui y sont a honorer ce jour-la.
 *
 * @param ordre                      rang de l'etape, a partir de 1
 * @param siteId                     identifiant du site
 * @param siteNom                    nom du site
 * @param latitude                   latitude du site
 * @param longitude                  longitude du site
 * @param planningIds                plannings a realiser sur ce site
 * @param nombreVisites              nombre de visites planifiees sur ce site
 * @param distanceDepuisPrecedenteMetres distance a vol d'oiseau depuis l'etape
 *                                   precedente ; zero pour la premiere
 */
public record EtapeTournee(
        int ordre,
        Long siteId,
        String siteNom,
        BigDecimal latitude,
        BigDecimal longitude,
        List<Long> planningIds,
        int nombreVisites,
        BigDecimal distanceDepuisPrecedenteMetres) {
}
