package com.zumm.web;

import java.time.Instant;

/**
 * Une modification porte sur une version qui n'est plus celle du serveur
 * (SPRINT-24, lot C).
 *
 * <p>Ferme la ligne « resolution de conflits multi-agents hors ligne » du §11 :
 * la file de mutations rejouait jusqu'ici ses ecritures sans jamais verifier que
 * la ressource n'avait pas bouge entre-temps. Deux agents redescendus du meme
 * rucher ecrasaient donc silencieusement le travail l'un de l'autre — le dernier
 * a retrouver du reseau gagnait, et personne n'etait informe.
 *
 * <p><strong>Pourquoi 409 et pas 412.</strong> {@code 412 Precondition Failed}
 * serait l'usage HTTP le plus strict, mais il ne transporte rien : l'appelant
 * apprend que sa requete a ete refusee, pas ce qui a change. Le 409 porte ici le
 * {@code maj_le} du serveur, ce qui permet a l'interface de proposer un vrai
 * choix — garder ma saisie, garder celle du serveur — au lieu d'un message
 * d'echec.
 *
 * <p><strong>Pourquoi le serveur ne fusionne pas.</strong> Fusionner deux
 * observations de la meme colonie reviendrait a decider, sans les avoir vues,
 * laquelle des deux dit vrai sur le couvain. C'est un arbitrage d'apiculteur.
 */
public class ConflitVersion extends RuntimeException {

    private final transient Instant versionServeur;

    public ConflitVersion(String ressource, Long id, Instant versionServeur) {
        super(("La %s %d a ete modifiee depuis votre derniere lecture "
                + "(version du serveur : %s).").formatted(ressource, id, versionServeur));
        this.versionServeur = versionServeur;
    }

    public Instant versionServeur() {
        return versionServeur;
    }
}
