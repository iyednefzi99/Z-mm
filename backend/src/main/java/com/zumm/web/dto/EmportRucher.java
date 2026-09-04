package com.zumm.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * Instantané d'un rucher, à emporter hors ligne (SPRINT-24, lot C).
 *
 * <p>Mise en œuvre de l'<a href="../../../../../../../roadmap/operationnel/06_decisions/ADR-012-hors-ligne-selectif.md">ADR-012</a>,
 * qui précise — sans le contredire — le refus de cacher {@code /api} inscrit
 * dans {@code vite.config.ts} au SPRINT-13. L'emport n'est pas un cache HTTP :
 * c'est une <strong>ressource explicite</strong>, demandée par l'utilisateur,
 * bornée à un rucher, horodatée par le serveur et purgeable.
 *
 * <p><strong>{@code preleveLe} est la raison d'être de ce DTO.</strong> Sans lui,
 * une donnée du disque passerait pour une donnée fraîche, et c'est exactement
 * l'objection de 2026 — juste, et à laquelle il fallait répondre plutôt que la
 * contourner. L'interface affiche cet instant partout où l'instantané sert, pas
 * seulement là où on l'a déclenché.
 *
 * <p><strong>Ce que l'instantané ne contient pas</strong>, et qui n'est pas un
 * oubli :
 *
 * <ul>
 *   <li>les <strong>mesures de capteurs</strong> — une courbe de poids figée
 *       trompe là où une liste de ruches ne trompe pas. C'est précisément ce que
 *       visait le commentaire de {@code vite.config.ts} ;
 *   <li>les <strong>positions exactes</strong> — {@code site} passe par
 *       {@code PolitiquePositions} comme toute autre sortie : emporter un rucher
 *       ne doit pas être un moyen d'obtenir en clair, sur un appareil sans
 *       session, ce que l'API dégrade en ligne ;
 *   <li>la <strong>météo</strong> — elle a déjà son repli, et une prévision
 *       vieille de trois jours est du bruit.
 * </ul>
 *
 * @param preleveLe        instant du prélèvement, donné par le serveur
 * @param site             fiche du rucher, position déjà masquée selon le rôle
 * @param ruches           colonies du rucher, clôturées comprises
 * @param dernieresVisites la dernière visite de chaque ruche, quand il y en a une
 * @param tachesOuvertes   ce qui reste à faire sur ces ruches
 * @param sousCarence      traitements dont la carence court : ces ruches ne se récoltent pas
 */
public record EmportRucher(
        Instant preleveLe,
        SiteReponse site,
        List<RucheReponse> ruches,
        List<VisiteReponse> dernieresVisites,
        List<TacheReponse> tachesOuvertes,
        List<TraitementReponse> sousCarence) {
}
