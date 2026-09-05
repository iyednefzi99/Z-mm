package com.zumm.web.dto;

import com.zumm.domain.Reine;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Vue exposee d'une reine (SPRINT-29, lot D).
 *
 * <p>Les libelles des objets lies — mere, ruche, serie — accompagnent leurs
 * identifiants : un arbre genealogique ou chaque noeud porte un numero se lit a
 * la loupe, et l'interface aurait a recharger la liste entiere pour l'afficher.
 *
 * @param mereCode  code de la mere, ou son identifiant a defaut : toutes les
 *                  reines ne sont pas numerotees
 */
public record ReineElevage(
        Long id,
        String code,
        Long mereId,
        String mereCode,
        Long rucheMereId,
        Long serieId,
        String serieNom,
        Long rucheId,
        String rucheModele,
        String origine,
        String fournisseur,
        String race,
        Integer anneeNaissance,
        String couleurMarquage,
        Boolean ailesClippees,
        LocalDate dateGreffage,
        LocalDate dateNaissance,
        LocalDate dateFecondation,
        LocalDate dateIntroduction,
        LocalDate dateFin,
        String statut,
        String note,
        Instant creeLe,
        Instant majLe) {

    public static ReineElevage de(Reine r) {
        return new ReineElevage(
                r.getId(),
                r.getCode(),
                r.getMere() == null ? null : r.getMere().getId(),
                r.getMere() == null ? null : nom(r.getMere()),
                r.getRucheMere() == null ? null : r.getRucheMere().getId(),
                r.getSerie() == null ? null : r.getSerie().getId(),
                r.getSerie() == null ? null : r.getSerie().getNom(),
                r.getRuche() == null ? null : r.getRuche().getId(),
                r.getRuche() == null ? null : r.getRuche().getModele(),
                r.getOrigine(),
                r.getFournisseur(),
                r.getRace(),
                r.getAnneeNaissance(),
                r.getCouleurMarquage(),
                r.getAilesClippees(),
                r.getDateGreffage(),
                r.getDateNaissance(),
                r.getDateFecondation(),
                r.getDateIntroduction(),
                r.getDateFin(),
                r.getStatut(),
                r.getNote(),
                r.getCreeLe(),
                r.getMajLe());
    }

    /** Le code s'il existe, l'identifiant sinon : un noeud d'arbre a besoin d'un nom. */
    public static String nom(Reine r) {
        return r.getCode() == null || r.getCode().isBlank()
                ? "#" + r.getId()
                : r.getCode();
    }
}
