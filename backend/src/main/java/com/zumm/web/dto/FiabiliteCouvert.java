package com.zumm.web.dto;

/**
 * Ce que le terrain a appris sur un millesime de la couche (SPRINT-33, lot K).
 *
 * <p><strong>Trois nombres, et aucun pourcentage.</strong> Un « taux d'exactitude
 * de 100 % » calcule sur deux parcelles verifiees serait lu comme un verdict sur
 * la couche entiere ; les nombres bruts, eux, portent leur propre reserve — on
 * voit tout de suite que la mesure repose sur deux visites. C'est le meme refus
 * qu'au SPRINT-23 pour la note globale de comparaison d'emplacements, et qu'au
 * SPRINT-32 pour le coefficient sante × flore : ne pas presenter du bruit comme
 * un resultat.
 *
 * @param parcelles  polygones du millesime
 * @param verifiees  parcelles portant un constat terrain
 * @param dementies  parcelles dont le constat CONTREDIT la source. C'est la
 *                   seule chose que le ground truthing etablisse vraiment
 * @param enAttente  parcelles marquees « a confirmer » et pas encore visitees
 */
public record FiabiliteCouvert(
        int millesime,
        int parcelles,
        int verifiees,
        int dementies,
        int enAttente) {
}
