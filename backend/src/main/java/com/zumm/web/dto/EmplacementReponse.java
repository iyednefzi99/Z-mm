package com.zumm.web.dto;

import com.zumm.domain.EmplacementSite;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un emplacement occupe par un rucher (SPRINT-21).
 *
 * <p><strong>Porte des coordonnees</strong> : ce DTO passe donc par
 * {@code PolitiquePositions}, comme {@link SiteReponse}. L'historique complet des
 * positions d'un rucher est une carte plus riche que sa seule position courante —
 * il dit aussi ou il se trouvait quand personne ne le surveillait.
 */
public record EmplacementReponse(
        Long id,
        Long siteId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal altitude,
        LocalDate dateDebut,
        LocalDate dateFin,
        String motif,
        String note,
        boolean courant) {

    public static EmplacementReponse de(EmplacementSite emplacement) {
        return new EmplacementReponse(
                emplacement.getId(),
                emplacement.getSite().getId(),
                emplacement.getLatitude(),
                emplacement.getLongitude(),
                emplacement.getAltitude(),
                emplacement.getDateDebut(),
                emplacement.getDateFin(),
                emplacement.getMotif(),
                emplacement.getNote(),
                emplacement.courant());
    }
}
