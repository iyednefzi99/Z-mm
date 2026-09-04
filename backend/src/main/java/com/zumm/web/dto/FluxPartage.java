package com.zumm.web.dto;

import java.util.List;

/**
 * Ce qu'un destinataire de partage voit (SPRINT-26, lot F1).
 *
 * <p><strong>Ce que ce DTO ne contient pas est aussi important que ce qu'il
 * contient</strong> : ni identifiant de ruche, ni rucher, ni ferme, ni position,
 * ni agent, ni alerte. Le destinataire est hors de l'exploitation ; il vient
 * regarder une courbe, pas explorer un cheptel. Lui rendre le `rucheId`
 * l'inviterait a le passer aux autres routes — qui le refuseraient, mais
 * l'invitation elle-meme est de trop.
 *
 * @param libelle libelle donne par celui qui partage : le destinataire doit
 *                savoir de quoi il regarde la courbe
 * @param serie   la serie journaliere, deja agregee
 */
public record FluxPartage(String libelle, List<PointJournalier> serie) {
}
