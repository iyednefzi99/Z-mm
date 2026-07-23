package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de requete pour creer ou mettre a jour un fermier (US-001).
 *
 * @param nom     nom de l'exploitant, obligatoire
 * @param contact coordonnee de contact, facultative
 */
public record FermierCorps(
        @NotBlank @Size(max = 120) String nom,
        @Size(max = 180) String contact) {
}
