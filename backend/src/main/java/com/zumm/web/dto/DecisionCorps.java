package com.zumm.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Corps d'une decision de superviseur sur un planning (US-008). Le motif n'est
 * utile qu'au refus.
 *
 * <p>La borne de longueur etait le dernier champ de texte libre a ne pas en
 * porter. La colonne {@code motif_refus} est un {@code TEXT} : sans borne, un
 * refus pouvait ecrire un mega-octet en base et le renvoyer a chaque lecture du
 * planning. La valeur retenue tient le cas legitime — une justification tient en
 * quelques lignes — et le depassement est refuse en 400 par la validation, pas
 * tronque en silence.
 *
 * @param motif justification, requise au refus, ignoree a l'approbation
 */
public record DecisionCorps(
        @Size(max = 1000) String motif) {
}
