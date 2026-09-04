package com.zumm.web.dto;

import com.zumm.domain.TypeIndicateur;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Poids attribue a un compartiment (SPRINT-26, lot F1).
 *
 * <p>Pas de {@code rucheId} : le compartiment porte deja sa ruche, et la
 * redemander ouvrirait la porte a une incoherence entre les deux — le genre de
 * champ que personne ne verifie jusqu'au jour ou il ment.
 *
 * @param compartimentId corps ou hausse pese
 * @param valeur         poids, en kilogrammes
 * @param instant        moment de la pesee ; absent, l'instant courant
 */
public record MesureCompartimentCorps(
        @NotNull Long compartimentId,
        @NotNull BigDecimal valeur,
        Instant instant) {

    /**
     * Le poids, et lui seul.
     *
     * <p>La constante existe pour que le service n'ecrive pas la valeur en dur :
     * la base la refuserait de toute facon ({@code ck_mesure_compartiment_indicateur}),
     * mais le message serait celui de PostgreSQL, pas celui du metier.
     */
    public TypeIndicateur typeIndicateur() {
        return TypeIndicateur.POIDS;
    }
}
