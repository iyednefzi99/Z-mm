package com.zumm.web.dto;

import com.zumm.domain.SerieElevage;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'une serie d'elevage (SPRINT-29, lot D).
 *
 * @param tauxAcceptation acceptees sur greffees, en pourcentage, ou {@code null}
 *                        tant que le compte n'est pas releve. CALCULE, jamais
 *                        stocke : le ranger en base creerait une valeur a
 *                        maintenir en coherence avec deux colonnes voisines
 * @param tauxReussite    fecondees sur greffees — le seul chiffre qui compte
 *                        vraiment : une serie bien acceptee dont aucune reine ne
 *                        revient de vol de fecondation n'a rien produit
 */
public record SerieReponse(
        Long id,
        String nom,
        LocalDate dateGreffage,
        Long soucheId,
        String soucheCode,
        Long rucheEleveuseId,
        String methode,
        Integer nbGreffees,
        Integer nbAcceptees,
        Integer nbNees,
        Integer nbFecondees,
        Integer tauxAcceptation,
        Integer tauxReussite,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static SerieReponse de(SerieElevage s) {
        return new SerieReponse(
                s.getId(),
                s.getNom(),
                s.getDateGreffage(),
                s.getSouche() == null ? null : s.getSouche().getId(),
                s.getSouche() == null ? null : ReineElevage.nom(s.getSouche()),
                s.getRucheEleveuse() == null ? null : s.getRucheEleveuse().getId(),
                s.getMethode(),
                s.getNbGreffees(),
                s.getNbAcceptees(),
                s.getNbNees(),
                s.getNbFecondees(),
                s.tauxAcceptation(),
                s.tauxReussite(),
                s.getNote(),
                s.getCreeLe(),
                s.getMajLe());
    }
}
