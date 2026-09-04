package com.zumm.web.dto;

import java.util.List;

/**
 * Ce qu'une operation de lot a reellement fait (SPRINT-23, lot B).
 *
 * <p><strong>Une operation de lot reussit rarement en entier, et ce n'est pas
 * une anomalie.</strong> Sur un rucher de quarante ruches, deux sont clôturees
 * depuis hier, une est sous carence : trente-sept succes et trois refus est le
 * resultat NORMAL. Une reponse qui se contenterait de 200 ou de 400 serait
 * fausse dans les deux cas.
 *
 * <p>Le rapport dit donc les deux, et surtout : il nomme les echecs avec leur
 * motif, pour que l'apiculteur sache quoi reprendre — et pour que le rejeu porte
 * sur ces trois ruches, pas sur les quarante.
 *
 * @param demandees  nombre de ruches designees apres deduplication
 * @param reussites  identifiants des lignes creees
 * @param echecs     ce qui n'est pas passe, et pourquoi
 */
public record RapportLot(
        int demandees,
        List<Long> reussites,
        List<EchecLot> echecs) {

    /** Un refus, rattache a sa ruche. */
    public record EchecLot(Long rucheId, String motif) {
    }

    public boolean partiel() {
        return !echecs.isEmpty() && !reussites.isEmpty();
    }
}
