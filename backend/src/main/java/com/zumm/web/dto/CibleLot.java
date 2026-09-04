package com.zumm.web.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Sur quelles ruches porte une operation de lot (SPRINT-23, lot B).
 *
 * <p>Deux facons de designer, et elles ne se valent pas :
 *
 * <ul>
 *   <li>{@code rucheIds} — une selection explicite, celle que fait un ecran ou
 *       un scan de QR codes successifs ;
 *   <li>{@code siteId} — TOUT un rucher, resolu par le serveur au moment de
 *       l'operation. C'est ce que demande le §6 (« recolte sur tout un rucher en
 *       une saisie ») et c'est aussi le seul moyen de ne pas faire transiter
 *       quarante identifiants que le client vient de lire.
 * </ul>
 *
 * <p>Les deux sont cumulables : scanner trente ruches d'un rucher qui en compte
 * quarante, puis ajouter le rucher entier, doit donner quarante lignes et non
 * soixante-dix. Le service deduplique.
 *
 * <p><strong>L'un des deux au moins est obligatoire</strong>, et le service le
 * verifie : un lot sans cible n'est pas un lot vide, c'est une erreur d'appel —
 * repondre « zero ruche traitee » laisserait croire que l'operation a eu lieu.
 *
 * @param rucheIds selection explicite
 * @param siteId   rucher entier, ruches CLOTUREES exclues
 */
public record CibleLot(
        @Size(max = 500) List<Long> rucheIds,
        Long siteId) {

    /** Vrai si aucune cible n'est designee — cas refuse par le service. */
    public boolean vide() {
        return (rucheIds == null || rucheIds.isEmpty()) && siteId == null;
    }
}
