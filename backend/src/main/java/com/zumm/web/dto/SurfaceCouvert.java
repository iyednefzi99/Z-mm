package com.zumm.web.dto;

import java.math.BigDecimal;

/**
 * Surface d'une classe de couvert dans le rayon de butinage (SPRINT-32, lot H).
 *
 * @param classe    culture | prairie | foret | lande | verger | vigne | eau |
 *                  urbain | sol_nu | autre — la taxonomie FERMEE de la `V31`
 * @param surfaceHa surface geodesique reelle, en hectares, bornee au cercle
 * @param part      part du cercle, en pourcent. Calculee au service, jamais
 *                  stockee — et {@code null} tant que le total n'est pas connu
 */
public record SurfaceCouvert(String classe, BigDecimal surfaceHa, BigDecimal part) {

    /** La meme surface, avec sa part du cercle. */
    public SurfaceCouvert avecPart(BigDecimal part) {
        return new SurfaceCouvert(classe, surfaceHa, part);
    }
}
