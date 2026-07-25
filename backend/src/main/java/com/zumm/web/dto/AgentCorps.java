package com.zumm.web.dto;

import com.zumm.domain.RoleAgent;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de requete pour creer ou mettre a jour un agent (US-005).
 *
 * @param nom      nom de l'agent, obligatoire
 * @param role     role metier (apiculteur / superviseur / responsable / admin)
 * @param fermeId  ferme d'appartenance, facultative (un role transverse n'en a pas) ;
 *                 si fournie, doit exister dans le tenant
 * @param email    adresse de notification (SPRINT-09), facultative
 */
public record AgentCorps(
        @NotBlank @Size(max = 120) String nom,
        @NotNull RoleAgent role,
        Long fermeId,
        @Email @Size(max = 180) String email) {
}
