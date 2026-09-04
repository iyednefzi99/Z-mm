package com.zumm.web.dto;

import com.zumm.domain.Transport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Vue exposee d'un plan de transhumance (SPRINT-21).
 *
 * <p><strong>Porte une position</strong> — celle de la destination — et passe
 * donc par {@code PolitiquePositions} comme {@link SiteReponse} et
 * {@link EmplacementReponse}. Un plan de transport est une carte des ruchers a
 * VENIR : la masquer importe autant que de masquer la carte actuelle.
 *
 * @param voyages nombre de trajets qu'impose la capacite, calcule et jamais
 *                stocke : il se deduit de deux colonnes, et le stocker
 *                autoriserait les trois valeurs a diverger
 */
public record TransportReponse(
        Long id,
        Long siteId,
        String siteNom,
        Long agentId,
        String agentNom,
        LocalDate datePrevue,
        LocalTime heurePrevue,
        String vehicule,
        Integer capaciteRuches,
        Integer nbRuches,
        Integer voyages,
        String destinationLibelle,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude,
        String statut,
        String note,
        Instant creeLe) {

    public static TransportReponse de(Transport transport) {
        return new TransportReponse(
                transport.getId(),
                transport.getSite().getId(),
                transport.getSite().getNom(),
                transport.getAgent().getId(),
                transport.getAgent().getNom(),
                transport.getDatePrevue(),
                transport.getHeurePrevue(),
                transport.getVehicule(),
                transport.getCapaciteRuches(),
                transport.getNbRuches(),
                transport.voyages(),
                transport.getDestinationLibelle(),
                transport.getDestinationLatitude(),
                transport.getDestinationLongitude(),
                transport.getStatut(),
                transport.getNote(),
                transport.getCreeLe());
    }

    /** Meme vue, position degradee : ce que rend la politique aux profils non proprietaires. */
    public TransportReponse avecPosition(BigDecimal latitude, BigDecimal longitude) {
        return new TransportReponse(id, siteId, siteNom, agentId, agentNom, datePrevue,
                heurePrevue, vehicule, capaciteRuches, nbRuches, voyages, destinationLibelle,
                latitude, longitude, statut, note, creeLe);
    }
}
