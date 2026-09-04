package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Un emplacement, aligne pour la comparaison (SPRINT-23, lot B).
 *
 * <p><strong>Aucune note globale, et c'est deliberе.</strong> Melanger des kilos,
 * des especes florales, une altitude et une densite de voisinage dans un score
 * unique donnerait un chiffre qui a l'autorite d'une mesure sans en avoir la
 * matiere. Les criteres restent cote a cote ; l'apiculteur tranche.
 *
 * <p><strong>Aucune position non plus</strong> : comparer trois emplacements est
 * precisement le moment ou l'on serait tente d'en donner les coordonnees, et la
 * comparaison n'en a pas besoin.
 *
 * @param rendementKgParRuche production des deux dernieres saisons, RAPPORTEE au
 *                            nombre de colonies — un rucher de vingt produit
 *                            mecaniquement plus qu'un rucher de cinq, ce qui ne
 *                            dit rien de l'emplacement. {@code null} si le rucher
 *                            n'a aucune ruche
 * @param ressourcesEnFleur   parmi les ressources declarees, celles dont la
 *                            fenetre de floraison couvre le mois en cours
 * @param ruchersA3km         ruchers de l'exploitation qui se partagent le meme
 *                            nectar : deux ruchers a huit cents metres ne sont
 *                            pas deux emplacements, c'est un seul
 */
public record ComparaisonSite(
        Long siteId,
        String siteNom,
        String ville,
        String typeSite,
        String exposition,
        BigDecimal altitude,
        int nbRuches,
        BigDecimal rendementKgParRuche,
        int ressourcesDeclarees,
        int ressourcesEnFleur,
        int ruchersA3km) {
}
