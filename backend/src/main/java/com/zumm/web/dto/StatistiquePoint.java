package com.zumm.web.dto;

/**
 * Ce qu'un point d'observation a donne sur une periode (SPRINT-28, lot I).
 *
 * <p>C'est le mot « analysables » de la ligne du §3 — « saisie par cases a
 * cocher (~50 points analysables) » — rendu vrai. Des cases que personne ne
 * peut compter ne sont pas analysables : ce sont des cases.
 *
 * <p><strong>{@code releves} n'est pas un nombre de visites.</strong> C'est le
 * nombre de fois ou le point a ete REGARDE, c'est-a-dire ou une valeur a ete
 * saisie. Les visites qui ne portaient pas ce point au gabarit n'y figurent
 * pas : rapporter les presences a toutes les visites de la periode donnerait un
 * taux systematiquement sous-estime, et rassurant a tort.
 *
 * @param presents      pour un point booleen, le nombre de « oui ». Toujours 0
 *                      sur un point d'echelle, ou la valeur ne se coche pas
 * @param moyenneEchelle moyenne de l'intensite, ou {@code null} pour un point
 *                      booleen — une moyenne de cases cochees serait un taux
 *                      deguise en note
 */
public record StatistiquePoint(
        String code,
        String libelle,
        String categorie,
        String typeValeur,
        Long releves,
        Long presents,
        Double moyenneEchelle) {
}
