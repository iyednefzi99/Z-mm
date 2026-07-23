package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de requete pour creer ou mettre a jour une ferme (US-002).
 *
 * @param nom       nom de la ferme, obligatoire
 * @param fermierId fermier proprietaire, obligatoire ; doit exister dans le tenant
 */
public record FermeCorps(
        @NotBlank @Size(max = 120) String nom,
        @NotNull Long fermierId) {
}
