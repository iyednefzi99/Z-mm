package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Site;
import com.zumm.domain.Transport;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.TransportRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.DemenagementCorps;
import com.zumm.web.dto.TransportCorps;
import com.zumm.web.dto.TransportReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planification des transhumances (SPRINT-21).
 *
 * <p>Repond a la derniere ligne ❌ du §1 de {@code docs/ECART-CONCURRENTS.md} :
 * l'historique d'emplacement savait constater un deplacement, jamais
 * l'organiser.
 *
 * <p><strong>Realiser un transport ne duplique pas le demenagement, il
 * l'appelle.</strong> {@link SiteService#demenager} reste le seul endroit qui
 * clot un emplacement et en ouvre un autre — deux implementations de cette regle
 * finiraient par diverger, et c'est l'historique du parc qui en porterait la
 * trace.
 */
@Service
@Transactional
public class TransportService {

    private final TransportRepository transports;
    private final SiteRepository sites;
    private final AgentRepository agents;
    private final SiteService siteService;
    private final PolitiquePositions positions;

    public TransportService(TransportRepository transports, SiteRepository sites,
            AgentRepository agents, SiteService siteService, PolitiquePositions positions) {
        this.transports = transports;
        this.sites = sites;
        this.agents = agents;
        this.siteService = siteService;
        this.positions = positions;
    }

    public TransportReponse planifier(TransportCorps corps) {
        Site site = sites.findById(corps.siteId()).orElseThrow(() ->
                new RequeteInvalide("Site inconnu dans ce tenant : " + corps.siteId()));
        Agent agent = agents.findById(corps.agentId()).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + corps.agentId()));
        verifierCoordonnees(corps);

        Transport transport = new Transport(site, agent, corps.datePrevue(),
                corps.destinationLibelle().trim());
        transport.setHeurePrevue(corps.heurePrevue());
        transport.setVehicule(corps.vehicule());
        transport.setCapaciteRuches(corps.capaciteRuches());
        transport.setNbRuches(corps.nbRuches());
        transport.setDestinationLatitude(corps.destinationLatitude());
        transport.setDestinationLongitude(corps.destinationLongitude());
        transport.setNote(corps.note());
        return vue(transports.save(transport));
    }

    @Transactional(readOnly = true)
    public List<TransportReponse> parSite(Long siteId) {
        if (sites.findById(siteId).isEmpty()) {
            throw RessourceIntrouvable.de("Site", siteId);
        }
        return transports.findBySite_IdOrderByDatePrevueDescIdDesc(siteId).stream()
                .map(this::vue).toList();
    }

    /** Ce qui reste a deplacer, a l'echelle de l'exploitation. */
    @Transactional(readOnly = true)
    public List<TransportReponse> prevus(LocalDate debut, LocalDate fin) {
        List<Transport> retenus = debut == null || fin == null
                ? transports.findByStatutOrderByDatePrevueAscIdAsc("prevu")
                : transports.findByStatutAndDatePrevueBetweenOrderByDatePrevueAscIdAsc(
                        "prevu", debut, fin);
        return retenus.stream().map(this::vue).toList();
    }

    /**
     * Execute le plan : le rucher demenage, le transport passe a {@code realise}.
     *
     * <p>Sans coordonnees de destination, le plan reste un plan : ouvrir un
     * emplacement sans position ferait un trou dans l'historique, et un trou dans
     * un historique ne se voit pas.
     */
    public TransportReponse realiser(Long id) {
        Transport transport = entite(id);
        if (!"prevu".equals(transport.getStatut())) {
            throw new RequeteInvalide("Ce transport est deja " + transport.getStatut() + ".");
        }
        if (!transport.destinationLocalisee()) {
            throw new RequeteInvalide(
                    "La destination n'a pas de coordonnees : le rucher ne peut pas etre deplace "
                            + "sans position. Completez-les avant de realiser le transport.");
        }
        siteService.demenager(transport.getSite().getId(), new DemenagementCorps(
                transport.getDestinationLatitude(),
                transport.getDestinationLongitude(),
                null,
                transport.getDatePrevue(),
                "transhumance",
                transport.getDestinationLibelle()));
        transport.setStatut("realise");
        return vue(transport);
    }

    public TransportReponse annuler(Long id) {
        Transport transport = entite(id);
        if ("realise".equals(transport.getStatut())) {
            throw new RequeteInvalide("Un transport realise ne s'annule pas : le rucher a bouge.");
        }
        transport.setStatut("annule");
        return vue(transport);
    }

    public void supprimer(Long id) {
        transports.delete(entite(id));
    }

    private Transport entite(Long id) {
        return transports.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Transport", id));
    }

    /**
     * Vue exposee, destination masquee.
     *
     * <p>Toute sortie de transport passe par ici, pour la meme raison que pour un
     * site : la destination d'une transhumance est une position de rucher, avec
     * une semaine d'avance.
     */
    private TransportReponse vue(Transport transport) {
        TransportReponse brut = TransportReponse.de(transport);
        if (brut.destinationLatitude() == null) {
            return brut;
        }
        BigDecimal[] masquee = positions.masquer(
                brut.destinationLatitude(), brut.destinationLongitude());
        return brut.avecPosition(masquee[0], masquee[1]);
    }

    /** Une latitude sans longitude ne designe rien : la base le refuse, on le dit avant. */
    private void verifierCoordonnees(TransportCorps corps) {
        boolean latitude = corps.destinationLatitude() != null;
        boolean longitude = corps.destinationLongitude() != null;
        if (latitude != longitude) {
            throw new RequeteInvalide(
                    "La destination demande les deux coordonnees, ou aucune des deux.");
        }
    }
}
