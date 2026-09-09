package com.zumm.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Exposition d'un rucher aux zones traitees DECLAREES (SPRINT-33, lot K).
 *
 * <p>Ferme la ligne « evaluation de l'exposition aux zones traitees » du §2,
 * restee 🟡 depuis le SPRINT-32 pour un motif exact : {@code distanceCultureM}
 * mesure la distance a une CULTURE, et aucune couche ouverte ne dit ce qui a ete
 * epandu ni quand.
 *
 * <p>Ce motif tient toujours. Ce qui a change est la question posee : au lieu de
 * chercher une source qui n'existe pas, on accueille celle qui existe —
 * l'apiculteur a qui le voisin annonce un traitement, qui voit passer un
 * pulverisateur, qui lit un avis affiche. Meme decision que D1 de l'ADR-015 : la
 * donnee est <strong>accueillie</strong>, jamais interrogee.
 *
 * <p><strong>Le silence n'est pas une garantie.</strong> Une couche declarative
 * est incomplete par construction. C'est pourquoi cette reponse porte
 * {@code declarations} et {@code derniereDeclaration} : « aucune zone a
 * proximite » doit se lire « rien ne m'a ete declare », jamais « rien n'a ete
 * epandu ». Une exploitation qui n'a jamais rien saisi le voit du premier coup
 * d'oeil — zero declaration, aucune date.
 *
 * @param rayonKm             rayon de butinage employe, celui du rucher ou le defaut
 * @param declarations        nombre de zones declarees dans le rayon, toutes dates
 *                            confondues. Zero ne veut pas dire « pas de traitement »
 * @param derniereDeclaration date de la declaration la plus recente dans le rayon,
 *                            ou {@code null}. Une couche qui n'a pas bouge depuis
 *                            deux ans ne decrit plus la saison en cours
 * @param distanceMinM        distance a la zone declaree la plus proche, ou
 *                            {@code null}
 * @param sousDelaiRentree    zones dont le delai de rentree n'est pas ecoule au
 *                            jour de la lecture. C'est la seule information de ce
 *                            document qui appelle une decision le jour meme
 * @param zones               les declarations du rayon, de la plus recente a la
 *                            plus ancienne
 */
public record ExpositionRucher(
        Long siteId,
        String siteNom,
        BigDecimal rayonKm,
        int declarations,
        LocalDate derniereDeclaration,
        Double distanceMinM,
        int sousDelaiRentree,
        List<ZoneTraiteeReponse> zones) {
}
