package com.zumm.web.dto;

import com.zumm.domain.Site;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'un site (US-003), avec le rappel de la ferme d'appartenance.
 */
public record SiteReponse(
        Long id,
        String nom,
        Long fermeId,
        String fermeNom,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal altitude,
        LocalDate dateMiseEnOeuvre,
        LocalDate dateDemenagement,
        LocalDate dateCloture,
        Instant creeLe,
        Instant majLe) {

    public static SiteReponse de(Site site) {
        return new SiteReponse(
                site.getId(),
                site.getNom(),
                site.getFerme().getId(),
                site.getFerme().getNom(),
                site.getLatitude(),
                site.getLongitude(),
                site.getAltitude(),
                site.getDateMiseEnOeuvre(),
                site.getDateDemenagement(),
                site.getDateCloture(),
                site.getCreeLe(),
                site.getMajLe());
    }
}
