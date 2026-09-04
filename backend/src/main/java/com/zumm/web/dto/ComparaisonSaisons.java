package com.zumm.web.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Une saison de production, comparee aux autres (SPRINT-27, lot E).
 *
 * <p>Ferme deux lignes qui n'en font qu'une : « comparaison des recoltes annee
 * par annee » (§6) et « servez-vous de l'historique de rotation sur 3 a 5 ans »
 * (§13, conseil de BeeGIS). Les agregats existants sont sur PERIODE GLISSANTE —
 * douze mois qui reculent chaque jour —, ce qui ne permet jamais de dire
 * « 2026 a mieux donne que 2025 ».
 *
 * <p><strong>Le rendement est PAR RUCHE, et il compte les ruches qui ont
 * produit</strong>, pas celles qui existaient. Une exploitation qui double son
 * cheptel double sa production sans rien ameliorer ; c'est le rendement qui dit
 * si la saison fut bonne.
 *
 * @param annee          annee civile — la saison apicole du nord la suit d'assez
 *                       pres, et une saison a cheval sur deux annees rendrait
 *                       toute comparaison ambigue
 * @param rendementKg    production divisee par le nombre de ruches ayant produit,
 *                       ou {@code null} si aucune n'a produit
 * @param parProduit     ce qui a ete recolte, produit par produit
 */
public record ComparaisonSaisons(
        int annee,
        BigDecimal productionMielKg,
        int ruchesProductives,
        BigDecimal rendementKg,
        int nombreRecoltes,
        List<ProduitSaison> parProduit) {

    /** Quantite recoltee d'un produit, dans son unite. */
    public record ProduitSaison(String typeProduit, String unite, BigDecimal quantite) {
    }
}
