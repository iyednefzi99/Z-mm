package com.zumm.web.dto;

import com.zumm.domain.Recolte;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fiche de tracabilite d'un lot (US-033), telle qu'obtenue en scannant le QR code :
 * origine (ruche, site, ferme), date et nature de la recolte.
 */
public record TraceReponse(
        String lot,
        Long rucheId,
        String rucheModele,
        String siteNom,
        String fermeNom,
        LocalDate dateRecolte,
        BigDecimal quantiteKg,
        String typeMiel) {

    public static TraceReponse de(Recolte r) {
        return new TraceReponse(
                r.getLot(),
                r.getRuche().getId(),
                r.getRuche().getModele(),
                r.getRuche().getSite().getNom(),
                r.getRuche().getFerme().getNom(),
                r.getDateRecolte(),
                r.getQuantiteKg(),
                r.getTypeMiel());
    }
}
