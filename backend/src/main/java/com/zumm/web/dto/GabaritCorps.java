package com.zumm.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Corps de requete pour creer ou modifier un gabarit d'inspection (SPRINT-28).
 *
 * <p>{@code points} est la liste ORDONNEE des codes retenus : la position dans
 * la liste fait l'ordre d'affichage. Demander au client un numero d'ordre par
 * point aurait produit des trous et des doublons a la premiere reorganisation.
 *
 * <p>Les quatre {@code noyau*} valent {@code true} par defaut : un gabarit qui
 * ne dit rien garde la grille du SPRINT-20 en entier, et le comportement
 * existant reste le comportement par defaut.
 */
public record GabaritCorps(
        @NotBlank @Size(max = 60) String nom,
        @Size(max = 200) String description,
        Boolean noyauCouvain,
        Boolean noyauReine,
        Boolean noyauCadres,
        Boolean noyauTemperament,
        Boolean parDefaut,
        Boolean actif,
        List<String> points) {

    /** Les codes retenus, jamais {@code null} : evite une garde a chaque appelant. */
    public List<String> pointsOuVide() {
        return points == null ? List.of() : points;
    }

    /** Un booleen facultatif, avec sa valeur par defaut. */
    public static boolean ouVrai(Boolean valeur) {
        return valeur == null || valeur;
    }
}
