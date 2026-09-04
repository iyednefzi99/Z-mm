package com.zumm.web;

/**
 * Une regle metier refuse l'operation (SPRINT-22, lot A).
 *
 * <p><strong>409, et non 400.</strong> La distinction n'est pas cosmetique : un
 * 400 dit « votre requete est mal formee, corrigez-la », ce qui est faux ici. La
 * requete est parfaitement valide — c'est l'ETAT du systeme qui s'y oppose, et
 * l'appelant ne peut rien corriger dans son corps de requete. Recolter une ruche
 * sous carence est une demande legitime a laquelle la reponse est « pas
 * maintenant, et voici pourquoi ».
 *
 * <p>C'est aussi ce qui permet a l'interface de reagir differemment : un 400
 * signale un champ fautif, un 409 ouvre la porte de sortie tracee — « forcer, en
 * indiquant le motif ».
 */
public class RegleMetierViolee extends RuntimeException {

    public RegleMetierViolee(String message) {
        super(message);
    }
}
