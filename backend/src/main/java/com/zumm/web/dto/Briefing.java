package com.zumm.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Briefing du jour (SPRINT-30, lot G).
 *
 * <p>Ferme la ligne « assistant / mentor IA, briefing quotidien » du §7, dont le
 * reproche etait : « l'IA de Zumm surveille des series de capteurs ; elle ne lit
 * pas l'historique d'une colonie et ne propose rien ».
 *
 * <p><strong>Ce briefing n'est pas un modele de langue, et il ne pretend pas
 * l'etre.</strong> C'est une lecture de l'etat de l'exploitation : alertes
 * ouvertes, taches echues, carences qui se terminent, colonies qu'on n'a pas
 * vues depuis longtemps. Chaque ligne CITE ce qui la fonde, et se verifie d'un
 * clic.
 *
 * <p>Le choix n'est pas une facilite. Un modele qui redigerait « votre colonie 12
 * semble affaiblie » a partir du meme etat produirait une phrase plus agreable et
 * moins verifiable — et le jour ou elle serait fausse, personne ne saurait dire
 * d'ou elle vient. Le moteur de regles du SPRINT-22 a tranche pareil : les
 * regles sont du code, pas une table parametrable.
 *
 * @param genereLe jour du briefing. Il ne se stocke pas : il se recalcule, et
 *                 celui d'hier n'a plus d'interet
 * @param lignes   ce qui merite l'attention aujourd'hui, du plus urgent au moins
 */
public record Briefing(LocalDate genereLe, List<Ligne> lignes) {

    /**
     * Un point d'attention.
     *
     * @param categorie alerte | tache | carence | visite | meteo
     * @param urgence   1 (a faire aujourd'hui) a 3 (a savoir)
     * @param titre     ce qui se passe, en une phrase
     * @param detail    ce qui le fonde — un compte, une date, un nom de ruche
     * @param rucheId   ruche concernee quand il y en a une, pour ouvrir la fiche
     */
    public record Ligne(String categorie, int urgence, String titre, String detail,
            Long rucheId) {
    }
}
