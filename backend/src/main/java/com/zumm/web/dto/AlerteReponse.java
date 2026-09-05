package com.zumm.web.dto;

import com.zumm.domain.Alerte;
import com.zumm.domain.TypeIndicateur;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Vue exposee d'une alerte (US-018).
 *
 * <p>La CATEGORIE s'y ajoute au SPRINT-31 : un depassement de seuil et une
 * chute brutale sans recolte ne demandent pas le meme geste. La premiere se
 * surveille, la seconde fait prendre la voiture — et l'ecran doit pouvoir les
 * distinguer sans lire le message.
 */
public record AlerteReponse(
        Long id,
        Long rucheId,
        String rucheModele,
        TypeIndicateur typeIndicateur,
        String categorie,
        String niveau,
        String message,
        BigDecimal valeurDeclenchement,
        boolean ouverte,
        Instant ouverteLe,
        Instant fermeeLe) {

    public static AlerteReponse de(Alerte a) {
        return new AlerteReponse(
                a.getId(),
                a.getRuche().getId(),
                a.getRuche().getModele(),
                a.getTypeIndicateur(),
                a.getCategorie(),
                a.getNiveau(),
                a.getMessage(),
                a.getValeurDeclenchement(),
                a.isOuverte(),
                a.getOuverteLe(),
                a.getFermeeLe());
    }
}
