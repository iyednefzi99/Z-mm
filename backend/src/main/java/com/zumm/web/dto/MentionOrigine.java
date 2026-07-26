package com.zumm.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mention d'origine prete a imprimer sur l'etiquette (US-056).
 *
 * @param texte    mention formatee dans la langue demandee
 * @param origines parts consolidees par pays, par ordre DECROISSANT
 * @param melange  vrai des que plus d'un pays entre dans le lot
 */
public record MentionOrigine(String texte, List<Part> origines, boolean melange) {

    /**
     * Part consolidee d'un pays.
     *
     * @param paysOrigine code ISO 3166-1 alpha-2
     * @param libelle     nom du pays dans la langue demandee
     * @param pourcentage part cumulee, arrondie a l'entier le plus proche
     */
    public record Part(String paysOrigine, String libelle, BigDecimal pourcentage) {
    }
}
