package com.zumm.service.regles;

import java.util.List;

/**
 * Une regle qui engendre des taches (SPRINT-22, lot A).
 *
 * <p>Le §3 de {@code docs/ECART-CONCURRENTS.md} reprochait a Zumm de savoir
 * enregistrer une tache sans jamais en proposer une : « six catalogues
 * recommandent ou programment ; {@code TacheService} ne fait qu'enregistrer ».
 * Cette interface est la reponse, et sa forme importe autant que son contenu.
 *
 * <p><strong>Une regle ne cree rien.</strong> Elle PROPOSE des
 * {@link TacheProposee} ; c'est {@code MoteurRegles} qui decide de les
 * enregistrer, et lui seul touche le repository. Deux consequences : une regle
 * se teste sans base, et l'anti-doublon vit a un seul endroit.
 *
 * <p><strong>Chaque regle porte son code et son explication.</strong> Une tache
 * qui apparait sans qu'on sache pourquoi est une tache qu'on ignore, puis une
 * liste qu'on n'ouvre plus. {@link #code()} est inscrit sur la tache produite,
 * et l'ecran s'en sert pour dire « proposee par la regle des delais de carence ».
 */
public interface RegleTache {

    /**
     * Identifiant stable, inscrit sur chaque tache produite.
     *
     * <p>Stable au sens fort : le renommer orphelinerait les taches deja
     * engendrees, qui ne sauraient plus se justifier. Il fait partie du contrat,
     * comme un nom de colonne.
     */
    String code();

    /**
     * Taches que la regle propose au jour donne.
     *
     * <p>La date est un PARAMETRE et non {@code LocalDate.now()} : c'est ce qui
     * rend les regles testables sans horloge injectee, et c'est la convention
     * deja suivie par {@code Traitement.sousCarence(LocalDate)}.
     */
    List<TacheProposee> proposer(java.time.LocalDate jour);
}
