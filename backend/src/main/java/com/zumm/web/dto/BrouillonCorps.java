package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Un brouillon de visite depose ou remplace (SPRINT-24).
 *
 * @param agentId  auteur ; un brouillon appartient a son agent, pas au tenant
 * @param rucheId  ruche saisie — la paire (agent, ruche) est unique
 * @param contenu  JSON du formulaire en cours, OPAQUE au serveur
 * @param appareil nom donne par le navigateur, pour que l'agent se reconnaisse
 */
public record BrouillonCorps(
        @NotNull Long agentId,
        @NotNull Long rucheId,
        // 256 Kio : un formulaire fait quelques kilo-octets. La borne existe pour
        // qu'une note vocale ne finisse jamais encodee en base64 ici.
        @NotBlank @Size(max = 262144) String contenu,
        @Size(max = 80) String appareil) {
}
