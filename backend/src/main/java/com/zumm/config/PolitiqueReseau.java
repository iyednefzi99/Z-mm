package com.zumm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Autorise ou interdit tout appel sortant du serveur (SPRINT-30, lot G).
 *
 * <p>Ferme la ligne « traitement local, sans aucun trafic sortant » du §8 de
 * {@code docs/ECART-CONCURRENTS.md}. Le reproche y était précis, et il était
 * juste : Zümm appelle {@code api.open-meteo.com} et délègue la détection
 * d'anomalie à un microservice ; les deux ont un repli, mais
 * <strong>aucune bascule explicite</strong> ne permettait de dire « cette
 * exploitation ne parle à personne ». HiveSense en fait un argument de vente.
 *
 * <p><strong>Un seul endroit, comme {@code PolitiquePositions}.</strong> Le
 * réglage ne se lit pas dans chaque client : il se lit ici, et chaque client
 * demande la permission. C'est ce qui rend la promesse vérifiable — on relit une
 * classe, pas six appels dispersés.
 *
 * <p><strong>Ce que la bascule ne fait pas.</strong> Elle n'empêche pas le
 * NAVIGATEUR d'aller chercher des tuiles de carte : ce trafic-là part du poste
 * de l'utilisateur, pas du serveur, et se coupe côté interface (voir le mode
 * local de la PWA). Promettre ici plus que ce qu'on tient serait pire que ne
 * rien promettre — l'exploitant croirait son réseau muet alors qu'il ne l'est
 * qu'à moitié.
 */
@Component
public class PolitiqueReseau {

    private static final Logger LOG = LoggerFactory.getLogger(PolitiqueReseau.class);

    private final boolean sortantAutorise;

    public PolitiqueReseau(@Value("${zumm.reseau.sortant:true}") boolean sortantAutorise) {
        this.sortantAutorise = sortantAutorise;
        if (!sortantAutorise) {
            // Une seule ligne au démarrage, et elle est nécessaire : un
            // exploitant qui cherche pourquoi sa météo est vide doit trouver la
            // réponse dans son journal, pas dans le code.
            LOG.info("Mode local : aucun appel sortant. Meteo et microservice IA desactives.");
        }
    }

    /** Vrai si le serveur a le droit de joindre un service hors de l'exploitation. */
    public boolean sortantAutorise() {
        return sortantAutorise;
    }
}
