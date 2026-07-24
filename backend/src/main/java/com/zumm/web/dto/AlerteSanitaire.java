package com.zumm.web.dto;

import com.zumm.domain.EtatSante;
import java.time.LocalDate;

/**
 * Alerte du tableau de bord sanitaire (US-014). Le niveau resume la situation :
 * {@code critique} (dernier etat sanitaire mauvais, ou ruche jamais visitee),
 * {@code attention} (delai sans visite depasse, ou etat moyen), {@code ok} sinon.
 *
 * @param joursDepuisVisite jours ecoules depuis la derniere visite, null si aucune
 */
public record AlerteSanitaire(
        Long rucheId,
        String rucheModele,
        EtatSante dernierEtatSante,
        LocalDate derniereVisite,
        Long joursDepuisVisite,
        String niveau,
        String motif) {

    public static final String OK = "ok";
    public static final String ATTENTION = "attention";
    public static final String CRITIQUE = "critique";
}
