package com.zumm.web.dto;

import java.util.List;

/**
 * Arbre de lignee d'une reine (SPRINT-29, lot D).
 *
 * <p>Ferme la ligne « généalogie / arbre de lignées » du §7 — que trois
 * concurrents mettent en avant.
 *
 * <p><strong>Deux listes plutot qu'un arbre unique</strong>, parce que les deux
 * sens ne se lisent pas de la meme facon. Les ascendants forment une CHAINE :
 * une reine a une mere, qui a une mere. Les descendants forment un ARBRE : une
 * bonne souche en donne trente. Les fondre dans une structure unique aurait
 * force l'interface a deviner de quel cote elle se trouve.
 *
 * <p>La profondeur est bornee (voir {@code ElevageService}) : une lignee
 * circulaire est refusee a l'ecriture, mais une base reprise d'ailleurs pourrait
 * en porter une, et une lecture ne doit jamais boucler.
 *
 * @param ascendants la chaine des meres, de la plus proche a la plus lointaine
 * @param descendants les filles, puis leurs filles, avec leur profondeur
 */
public record Genealogie(
        ReineElevage reine,
        List<Noeud> ascendants,
        List<Noeud> descendants) {

    /**
     * Un maillon de la lignee.
     *
     * @param profondeur 1 pour une mere ou une fille directe, 2 pour une
     *                   grand-mere ou une petite-fille, etc.
     */
    public record Noeud(Long id, String code, Integer profondeur, Long parentId, String race,
            Integer anneeNaissance, String statut) {
    }
}
