package com.zumm.web.dto;

import com.zumm.domain.Site;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Vue exposee d'un site (US-003), avec le rappel de la ferme d'appartenance.
 *
 * <p><strong>Ce DTO est le point sensible du produit.</strong> Il porte la
 * position d'un rucher et, depuis le SPRINT-21, son adresse postale : il ne sort
 * jamais sans passer par {@code PolitiquePositions}, qui arrondit l'une et retire
 * l'autre pour les profils non proprietaires. Une rue situe un rucher au portail
 * la ou deux decimales le situent au kilometre — l'adresse est donc masquee plus
 * fort que les coordonnees, pas moins.
 *
 * @param ressources sources de nectar declarees. Vide dans les vues
 *                   cartographiques ({@link #de(Site)}), renseignee dans la liste
 *                   et la fiche : les charger pour chaque membre d'une grappe
 *                   ferait une requete par site pour une donnee que la carte
 *                   n'affiche pas.
 */
public record SiteReponse(
        Long id,
        String nom,
        Long fermeId,
        String fermeNom,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal altitude,
        BigDecimal rayonButinageKm,
        LocalDate dateMiseEnOeuvre,
        LocalDate dateDemenagement,
        LocalDate dateCloture,
        String adresseRue,
        String codePostal,
        String ville,
        String pays,
        String typeSite,
        String exposition,
        List<RessourceFloraleReponse> ressources,
        String priorite,
        /**
         * Couverture mobile constatee sur place (SPRINT-24). NULLE = inconnue.
         *
         * <p>Ce n'est pas une position et elle n'est donc pas masquee : savoir
         * qu'un rucher est en zone blanche ne dit pas ou il est, et c'est
         * precisement l'information dont un agent a besoin AVANT de partir.
         */
        String couvertureReseau,
        Instant creeLe,
        Instant majLe) {

    /** Vue sans les ressources florales : carte, grappes, voisins. */
    public static SiteReponse de(Site site) {
        return de(site, List.of());
    }

    public static SiteReponse de(Site site, List<RessourceFloraleReponse> ressources) {
        return new SiteReponse(
                site.getId(),
                site.getNom(),
                site.getFerme().getId(),
                site.getFerme().getNom(),
                site.getLatitude(),
                site.getLongitude(),
                site.getAltitude(),
                site.getRayonButinageKm(),
                site.getDateMiseEnOeuvre(),
                site.getDateDemenagement(),
                site.getDateCloture(),
                site.getAdresseRue(),
                site.getCodePostal(),
                site.getVille(),
                site.getPays(),
                site.getTypeSite(),
                site.getExposition(),
                ressources,
                site.getPriorite(),
                site.getCouvertureReseau(),
                site.getCreeLe(),
                site.getMajLe());
    }
}
