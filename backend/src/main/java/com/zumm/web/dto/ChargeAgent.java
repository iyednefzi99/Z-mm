package com.zumm.web.dto;

/**
 * Ce qu'un agent a sur les bras (SPRINT-23, lot B).
 *
 * <p>Ferme le 🟡 « coordination d'equipes terrain, logistique multi-sites » du
 * §7. Zumm savait qui est responsable de quoi ; il ne savait pas dire qui est
 * deborde, ni quel rucher n'a personne.
 *
 * <p><strong>Ce n'est pas une notation.</strong> Un agent avec dix taches en
 * retard a probablement eu trois jours de pluie, pas un probleme de rigueur. Ces
 * chiffres servent a repartir une charge, jamais a comparer des personnes — et
 * l'ecran doit le dire.
 *
 * @param ruchersConcernes ruchers ou l'agent a au moins une ruche : trois ruches
 *                         sur trois ruchers, c'est trois deplacements, ce que le
 *                         seul compte de ruches masquerait
 * @param tachesEnRetard   taches non faites dont l'echeance est passee
 * @param visites7Jours    plannings des sept prochains jours, refus exclus
 */
public record ChargeAgent(
        Long agentId,
        String agentNom,
        String role,
        int ruchesResponsable,
        int ruchersConcernes,
        long tachesOuvertes,
        long tachesEnRetard,
        long tachesCritiques,
        long visites7Jours) {
}
