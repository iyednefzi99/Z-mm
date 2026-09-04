package com.zumm.web.dto;

/**
 * Etat du jeu de demonstration (SPRINT-25, lot J).
 *
 * @param disponible la fonction est-elle ouverte sur ce deploiement
 *                   ({@code zumm.demonstration.activee}) ? Fausse en production
 * @param charge     un jeu est-il actuellement en place ?
 * @param objets     combien d'objets la purge retirerait — le chiffre qui rend
 *                   le bouton « Retirer » decidable plutot qu'inquietant
 */
public record EtatDemonstration(boolean disponible, boolean charge, int objets) {
}
