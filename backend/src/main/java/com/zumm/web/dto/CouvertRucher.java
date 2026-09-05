package com.zumm.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce qu'il y a autour d'un rucher (SPRINT-32, lot H).
 *
 * <p>Ferme les lignes « couches d'occupation du sol » et « calcul des surfaces
 * par type de couvert dans le rayon » du §2 — le domaine de BeeGIS, seul
 * concurrent a jouer sur le terrain que Zumm revendique.
 *
 * <p><strong>Ce document dit toujours d'ou il parle.</strong> `source` et
 * `millesime` accompagnent les surfaces sans exception : « 42 % de cultures »
 * n'engage personne tant qu'on ne sait pas de quelle annee et de quel jeu de
 * donnees cela vient. C'est la ligne « millesime des donnees
 * environnementales » du §13, et elle n'est pas decorative — les parcelles
 * tournent d'une annee sur l'autre.
 *
 * @param rayonKm            rayon reellement employe, celui du rucher ou le defaut
 * @param surfaceCercleHa    surface du cercle entier, pour que les parts se verifient
 * @param couverte           part du cercle effectivement decrite par la couche.
 *                           Une couche incomplete ne doit pas se lire comme un
 *                           environnement vide : 30 % de couverture et 70 % de
 *                           silence ne disent pas « 70 % de sol nu »
 * @param distanceCultureM   distance a la parcelle cultivee la plus proche, ou
 *                           {@code null}. Ce n'est PAS une distance a une zone
 *                           traitee : aucune couche ouverte ne dit ce qui a ete
 *                           epandu
 */
public record CouvertRucher(
        Long siteId,
        String siteNom,
        BigDecimal rayonKm,
        Integer millesime,
        String source,
        BigDecimal surfaceCercleHa,
        BigDecimal couverte,
        Double distanceCultureM,
        List<SurfaceCouvert> surfaces) {
}
