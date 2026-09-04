package com.zumm.service.regles;

import java.time.LocalDate;

/**
 * Une tache qu'une regle propose (SPRINT-22, lot A).
 *
 * <p>Volontairement un enregistrement inerte : une regle ne connait ni le
 * repository, ni l'entite {@code Tache}. Elle decrit ce qu'il faudrait faire, et
 * {@code MoteurRegles} traduit.
 *
 * @param cle       identite de la tache — {@code carence-retrait:42}. C'est elle
 *                  qui empeche la regle de recreer la meme tache a chaque
 *                  passage ; un index unique la fait respecter en base
 * @param libelle   ce que l'apiculteur lira
 * @param rucheId   ruche concernee, ou {@code null} pour une tache d'exploitation
 * @param materielId equipement concerne (SPRINT-27), ou {@code null}
 * @param echeance  quand elle doit etre faite
 * @param priorite  basse | normale | haute | critique
 * @param categorie controle | traitement | nourrissement | recolte | materiel |
 *                  elevage | administratif | autre
 */
public record TacheProposee(
        String cle,
        String libelle,
        Long rucheId,
        Long materielId,
        LocalDate echeance,
        String priorite,
        String categorie) {

    /**
     * Tache portant sur une ruche — le cas de toutes les regles du SPRINT-22.
     *
     * <p>Ce constructeur existe pour que l'ajout du materiel (SPRINT-27) ne
     * force pas cinq regles a se declarer « sans materiel » : elles n'ont pas a
     * connaitre une notion qui ne les concerne pas.
     */
    public TacheProposee(String cle, String libelle, Long rucheId, LocalDate echeance,
            String priorite, String categorie) {
        this(cle, libelle, rucheId, null, echeance, priorite, categorie);
    }
}
