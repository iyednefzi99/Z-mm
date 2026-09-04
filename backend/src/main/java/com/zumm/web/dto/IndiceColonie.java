package com.zumm.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Indices calcules d'une colonie (SPRINT-22, lot A).
 *
 * <p><strong>{@code composantes} n'est pas decoratif</strong> : c'est le nombre
 * d'observations qui ont reellement pese dans le calcul. A zero, {@code sante} et
 * {@code risqueEssaimage} valent zero eux aussi, et ne veulent rien dire — la
 * colonie n'est pas en mauvaise sante, elle est INCONNUE. L'ecran doit alors
 * afficher « non evalue » et surtout pas une jauge, qui ferait passer l'ignorance
 * pour un diagnostic.
 *
 * @param sante           0 a 100, 100 etant une colonie sans signe defavorable
 * @param risqueEssaimage 0 a 100
 * @param composantes     nombre d'observations prises en compte
 * @param derniereVisite  date de la visite qui a servi, ou {@code null}
 * @param motifs          codes des penalites appliquees, pour expliquer la note
 *                        plutot que de l'asséner
 */
public record IndiceColonie(
        Long rucheId,
        String rucheModele,
        int sante,
        int risqueEssaimage,
        int composantes,
        LocalDate derniereVisite,
        List<String> motifs) {

    /** Vrai quand rien n'a pu etre evalue : l'ecran ne doit alors rien affirmer. */
    public boolean nonEvalue() {
        return composantes == 0;
    }
}
