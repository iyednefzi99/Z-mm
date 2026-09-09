package com.zumm.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une parcelle de la couche d'occupation du sol, et l'etat de sa verification
 * terrain (SPRINT-33, lot K).
 *
 * <p>Ferme la ligne « ground truthing » du §13 : BeeGIS conseille a ses
 * utilisateurs de <em>verifier au printemps la culture reellement semee</em>,
 * parce qu'une couche d'occupation du sol se trompe — un millesime en retard,
 * une rotation non captee, une parcelle retournee en avril.
 *
 * <p><strong>Les deux classes coexistent, et c'est le point.</strong>
 * {@code classe} est ce que la source affirme ; {@code classeConstatee} ce que
 * le terrain a montre. Ecraser la premiere par la seconde ferait raconter a la
 * parcelle que la source avait raison depuis le debut, et la fiabilite d'un
 * millesime ne se mesurerait plus. C'est la meme construction que la floraison
 * declaree de la `V21` et la floraison observee de la `V31`.
 *
 * @param classe          ce que la source affirme — taxonomie fermee de la `V31`
 * @param classeConstatee ce que le terrain a montre, ou {@code null}. Des qu'il
 *                        existe, c'est LUI que les surfaces comptent
 * @param aConfirmer      doute pose a la main. Jamais deduit : une regle qui
 *                        marquerait d'office fabriquerait une charge de travail
 *                        que personne n'a demandee
 * @param surfaceHa       surface geodesique. Bornee au rayon quand la lecture
 *                        porte sur un rucher, entiere sinon
 */
public record ParcelleCouvert(
        Long id,
        String classe,
        String classeConstatee,
        String source,
        int millesime,
        boolean aConfirmer,
        LocalDate constateLe,
        String constatNote,
        BigDecimal surfaceHa) {

    /** Ce que les surfaces comptent : le constat s'il existe, la source sinon. */
    public String classeRetenue() {
        return classeConstatee != null ? classeConstatee : classe;
    }

    /**
     * Le terrain a-t-il dementi la source ?
     *
     * <p>C'est la seule statistique que le ground truthing produise vraiment :
     * non pas « la couche est bonne », mais « sur les parcelles verifiees, elle
     * s'est trompee tant de fois ».
     */
    public boolean dement() {
        return classeConstatee != null && !classeConstatee.equals(classe);
    }
}
