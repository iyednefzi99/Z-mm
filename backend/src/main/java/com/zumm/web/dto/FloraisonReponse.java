package com.zumm.web.dto;

import com.zumm.domain.FloraisonObservee;
import java.time.LocalDate;

/**
 * Vue exposee d'une floraison observee (SPRINT-32, lot H).
 *
 * @param moisDeclare mois de debut DECLARE sur la ressource (`V21`), quand il
 *                    existe. Rendu a cote de l'observe pour que l'ecart se lise
 *                    d'un coup : c'est tout l'interet d'avoir gardé les deux
 * @param ecartJours  ecart entre le debut observe et le premier jour du mois
 *                    declare, ou {@code null} si rien n'etait declare. Positif =
 *                    en retard sur la prevision
 */
public record FloraisonReponse(
        Long id,
        Long ressourceId,
        String ressource,
        Long siteId,
        Integer annee,
        LocalDate dateDebut,
        LocalDate datePic,
        LocalDate dateFin,
        Integer abondance,
        Integer moisDeclare,
        Integer ecartJours,
        String note) {

    public static FloraisonReponse de(FloraisonObservee f) {
        Integer moisDeclare = f.getRessource().getMoisDebut();
        return new FloraisonReponse(
                f.getId(),
                f.getRessource().getId(),
                f.getRessource().getRessource(),
                f.getRessource().getSite() == null ? null : f.getRessource().getSite().getId(),
                f.getAnnee(),
                f.getDateDebut(),
                f.getDatePic(),
                f.getDateFin(),
                f.getAbondance() == null ? null : f.getAbondance().intValue(),
                moisDeclare,
                ecart(f, moisDeclare),
                f.getNote());
    }

    /**
     * Ecart a la prevision, en jours.
     *
     * <p>Compare au PREMIER jour du mois declare, et non a son milieu : le
     * declaratif ne donne qu'un mois, et lui inventer une precision qu'il n'a
     * pas ferait lire un ecart de quinze jours la ou il n'y en a aucun.
     */
    private static Integer ecart(FloraisonObservee f, Integer moisDeclare) {
        if (moisDeclare == null) {
            return null;
        }
        LocalDate prevu = LocalDate.of(f.getAnnee(), moisDeclare, 1);
        return (int) java.time.temporal.ChronoUnit.DAYS.between(prevu, f.getDateDebut());
    }
}
