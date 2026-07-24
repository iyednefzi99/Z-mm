package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Corps de requete pour creer ou mettre a jour une tache (US-031).
 *
 * @param libelle  intitule de la tache, obligatoire
 * @param rucheId  ruche concernee (optionnel) ; doit exister dans le tenant
 * @param agentId  agent assigne (optionnel) ; doit exister dans le tenant
 * @param echeance date d'echeance (optionnel), alimente les rappels
 * @param faite    tache accomplie
 */
public record TacheCorps(
        @NotBlank @Size(max = 200) String libelle,
        Long rucheId,
        Long agentId,
        LocalDate echeance,
        boolean faite) {
}
