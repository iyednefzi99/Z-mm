package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Planning;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.StatutPlanning;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.PlanningRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.EtapeTournee;
import com.zumm.web.dto.PlanningCorps;
import com.zumm.web.dto.PlanningReponse;
import com.zumm.web.dto.TourneeReponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planification des visites et decision du superviseur (US-007, US-008).
 */
@Service
@Transactional
public class PlanningService {

    private final PlanningRepository plannings;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final SiteRepository sites;

    public PlanningService(PlanningRepository plannings, RucheRepository ruches,
                           AgentRepository agents, SiteRepository sites) {
        this.plannings = plannings;
        this.ruches = ruches;
        this.agents = agents;
        this.sites = sites;
    }

    public PlanningReponse creer(PlanningCorps corps) {
        Planning planning = new Planning(
                rucheRequise(corps.rucheId()),
                agentRequis(corps.agentId()),
                corps.datePrevue(),
                corps.raison() == null ? RaisonVisite.CONTROLE : corps.raison());
        planning.setSuperviseur(agentEventuel(corps.superviseurId()));
        planning.setHeurePrevue(corps.heurePrevue());
        planning.setDureeMin(corps.dureeMin());
        return PlanningReponse.de(plannings.save(planning));
    }

    @Transactional(readOnly = true)
    public List<PlanningReponse> lister() {
        return plannings.findAll().stream().map(PlanningReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public PlanningReponse obtenir(Long id) {
        return PlanningReponse.de(entite(id));
    }

    public PlanningReponse mettreAJour(Long id, PlanningCorps corps) {
        Planning planning = entite(id);
        planning.setRuche(rucheRequise(corps.rucheId()));
        planning.setAgent(agentRequis(corps.agentId()));
        planning.setSuperviseur(agentEventuel(corps.superviseurId()));
        planning.setDatePrevue(corps.datePrevue());
        planning.setHeurePrevue(corps.heurePrevue());
        planning.setDureeMin(corps.dureeMin());
        if (corps.raison() != null) {
            planning.setRaison(corps.raison());
        }
        return PlanningReponse.de(planning);
    }

    public void supprimer(Long id) {
        plannings.delete(entite(id));
    }

    /** US-008 : le superviseur approuve un planning. */
    public PlanningReponse approuver(Long id) {
        Planning planning = entite(id);
        planning.setStatut(StatutPlanning.APPROUVE);
        planning.setMotifRefus(null);
        return PlanningReponse.de(planning);
    }

    /** US-008 : le superviseur refuse un planning, motif obligatoire. */
    public PlanningReponse refuser(Long id, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new RequeteInvalide("Un refus doit être motivé.");
        }
        Planning planning = entite(id);
        planning.setStatut(StatutPlanning.REFUSE);
        planning.setMotifRefus(motif);
        return PlanningReponse.de(planning);
    }

    /**
     * Ordre de tournee propose a un agent pour une journee (US-047).
     *
     * <p>Les plannings sont regroupes par site — deux ruches d'un meme rucher ne font
     * qu'un deplacement. Les distances sont geodesiques, calculees par PostGIS, mais
     * <b>a vol d'oiseau</b> : le routage sur reseau routier est hors perimetre. L'ordre
     * sort d'une heuristique ({@link OptimiseurTournee}), il n'est pas optimal.
     *
     * @param departSiteId site par lequel commencer ; a defaut, le premier site de
     *                     l'ordre saisi par l'agent (heure prevue croissante)
     */
    @Transactional(readOnly = true)
    public TourneeReponse tournee(Long agentId, LocalDate date, Long departSiteId) {
        Agent agent = agentRequis(agentId);
        List<Planning> duJour = plannings.parAgentEtDate(agentId, date, StatutPlanning.REFUSE);

        // L'ordre de saisie est conserve : c'est le repli quand aucun depart n'est impose.
        Map<Long, List<Planning>> parSite = duJour.stream().collect(Collectors.groupingBy(
                p -> p.getRuche().getSite().getId(), LinkedHashMap::new, Collectors.toList()));
        List<Long> siteIds = new ArrayList<>(parSite.keySet());

        if (siteIds.isEmpty()) {
            return new TourneeReponse(agent.getId(), agent.getNom(), date, 0, 0,
                    BigDecimal.ZERO, List.of());
        }
        if (departSiteId != null && !siteIds.contains(departSiteId)) {
            throw new RequeteInvalide(
                    "Le site de depart ne figure pas dans la tournee du jour : " + departSiteId);
        }

        int depart = departSiteId == null ? 0 : siteIds.indexOf(departSiteId);
        double[][] distances = matriceDistances(siteIds);
        int[] ordre = OptimiseurTournee.ordonner(distances, depart);

        List<EtapeTournee> etapes = new ArrayList<>();
        double totale = 0;
        for (int rang = 0; rang < ordre.length; rang++) {
            double depuisPrecedente = rang == 0 ? 0 : distances[ordre[rang - 1]][ordre[rang]];
            totale += depuisPrecedente;
            Long siteId = siteIds.get(ordre[rang]);
            List<Planning> surPlace = parSite.get(siteId);
            Site site = surPlace.get(0).getRuche().getSite();
            etapes.add(new EtapeTournee(
                    rang + 1,
                    siteId,
                    site.getNom(),
                    site.getLatitude(),
                    site.getLongitude(),
                    surPlace.stream().map(Planning::getId).toList(),
                    surPlace.size(),
                    metres(depuisPrecedente)));
        }
        return new TourneeReponse(agent.getId(), agent.getNom(), date, siteIds.size(),
                duJour.size(), metres(totale), etapes);
    }

    /**
     * Matrice symetrique des distances entre les sites de la tournee. La base ne rend
     * qu'une ligne par paire ({@code a.id < b.id}) : la symetrie est retablie ici.
     */
    private double[][] matriceDistances(List<Long> siteIds) {
        Map<Long, Integer> rang = new HashMap<>();
        for (int i = 0; i < siteIds.size(); i++) {
            rang.put(siteIds.get(i), i);
        }
        double[][] distances = new double[siteIds.size()][siteIds.size()];
        for (SiteRepository.PaireDistance paire : sites.distancesEntre(siteIds)) {
            Integer depart = rang.get(paire.getDepartId());
            Integer arrivee = rang.get(paire.getArriveeId());
            if (depart != null && arrivee != null) {
                distances[depart][arrivee] = paire.getDistanceMetres();
                distances[arrivee][depart] = paire.getDistanceMetres();
            }
        }
        return distances;
    }

    private static BigDecimal metres(double valeur) {
        return BigDecimal.valueOf(valeur).setScale(1, RoundingMode.HALF_UP);
    }

    Planning entite(Long id) {
        return plannings.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Planning", id));
    }

    private Ruche rucheRequise(Long id) {
        return ruches.findById(id).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + id));
    }

    private Agent agentRequis(Long id) {
        return agents.findById(id).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + id));
    }

    private Agent agentEventuel(Long id) {
        return id == null ? null : agentRequis(id);
    }
}
