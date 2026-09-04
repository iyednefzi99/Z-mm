package com.zumm.web.dto;

/**
 * Une reponse de la recherche globale (SPRINT-21, §1 de
 * {@code docs/ECART-CONCURRENTS.md}).
 *
 * <p>Volontairement pauvre : un type, un identifiant, un libelle, une precision
 * et la route ou aller. Rien de ce qui pourrait servir a moissonner — ni
 * position, ni adresse, ni courriel. Une recherche transverse est le seul endroit
 * du produit ou un appelant obtient d'un coup un echantillon de TOUTES les
 * tables ; elle doit donc rendre le minimum qui permette de cliquer.
 *
 * @param type      famille de l'objet : site, ruche, ferme, fermier, agent,
 *                  recolte, lot
 * @param id        identifiant dans sa table
 * @param libelle   ce qui a repondu au motif
 * @param precision contexte court (commune, site, date...), facultatif
 * @param route     chemin de la PWA ou l'objet se consulte
 */
public record ResultatRecherche(
        String type,
        Long id,
        String libelle,
        String precision,
        String route) {
}
