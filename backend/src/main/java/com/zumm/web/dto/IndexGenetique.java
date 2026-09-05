package com.zumm.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Index genetique multicritere d'une reine (SPRINT-29, lot D).
 *
 * <p>Ferme la ligne « index génétique multicritère (hygiène, résistance varroa,
 * douceur) » du §7. Sa dependance etait levee depuis le SPRINT-20 : les criteres
 * d'inspection dont il decoule existent.
 *
 * <p><strong>Il n'y a AUCUNE note globale, et c'est la decision du lot.</strong>
 * La douceur, un taux d'infestation et des kilogrammes de miel ne s'additionnent
 * pas. Les ramener a un seul nombre demanderait une ponderation — 30 % de
 * douceur, 40 % de resistance ? — que personne n'a demandee, qui ne se justifie
 * par rien, et qui serait pourtant le seul chiffre que l'on retiendrait. C'est
 * le meme refus que celui de la comparaison d'emplacements au SPRINT-23, ou
 * « des kilos, des especes florales et une altitude ne s'additionnent pas ».
 *
 * <p><strong>Chaque critere porte son nombre d'observations</strong>, et vaut
 * {@code null} en dessous du minimum requis. Une douceur calculee sur une seule
 * visite n'est pas une douceur : c'est une anecdote, et l'afficher comme une
 * note ferait ecarter une reine sur un mauvais jour.
 *
 * <p>Rien n'est stocke : tout se recalcule a chaque lecture, comme les indices
 * de colonie du SPRINT-22.
 *
 * @param debut  debut du regne pris en compte — les observations anterieures
 *               appartiennent a la reine precedente
 * @param fin    fin du regne, ou la date du jour si la reine est en service
 * @param ruche  ruche sur laquelle le regne est observe ; sans elle, aucun
 *               critere ne peut etre calcule
 */
public record IndexGenetique(
        Long reineId,
        String code,
        Long rucheId,
        LocalDate debut,
        LocalDate fin,
        List<Critere> criteres) {

    /**
     * Un critere observe.
     *
     * @param code         douceur | resistance_varroa | production | essaimage
     *                     | hygiene
     * @param valeur       la valeur brute, dans son unite propre — jamais
     *                     ramenee a une echelle commune, qui ferait croire que
     *                     les criteres se comparent entre eux
     * @param unite        l'unite de cette valeur, a afficher avec elle
     * @param observations nombre d'observations qui la fondent
     * @param suffisant    faux quand les observations manquent : la valeur est
     *                     alors {@code null}, et l'ecran doit le dire plutot que
     *                     d'afficher un tiret qui passerait pour un zero
     */
    public record Critere(String code, BigDecimal valeur, String unite, int observations,
            boolean suffisant) {

        /** Un critere qu'on ne peut pas fonder : il se dit, il ne s'invente pas. */
        public static Critere insuffisant(String code, String unite, int observations) {
            return new Critere(code, null, unite, observations, false);
        }
    }
}
