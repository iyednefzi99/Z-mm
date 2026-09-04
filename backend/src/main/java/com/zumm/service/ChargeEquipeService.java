package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Planning;
import com.zumm.domain.Ruche;
import com.zumm.domain.StatutPlanning;
import com.zumm.domain.Tache;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.PlanningRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.web.dto.ChargeAgent;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Charge de travail par agent (SPRINT-23, lot B).
 *
 * <p>Ferme le 🟡 « coordination d'equipes terrain, logistique multi-sites » du
 * §7 : le depot savait qui est responsable de quelle ruche, sans jamais dire qui
 * est deborde.
 *
 * <p><strong>Ce service ne note personne.</strong> Les chiffres servent a
 * repartir une charge ; un tableau de bord qui s'en servirait pour comparer des
 * personnes ferait un usage que personne n'a demande, et que le metier ne
 * soutient pas — dix taches en retard, c'est le plus souvent trois jours de
 * pluie.
 */
@Service
@Transactional(readOnly = true)
public class ChargeEquipeService {

    private static final int HORIZON_JOURS = 7;

    private final AgentRepository agents;
    private final RucheRepository ruches;
    private final TacheRepository taches;
    private final PlanningRepository plannings;

    public ChargeEquipeService(AgentRepository agents, RucheRepository ruches,
            TacheRepository taches, PlanningRepository plannings) {
        this.agents = agents;
        this.ruches = ruches;
        this.taches = taches;
        this.plannings = plannings;
    }

    /** Charge de chaque agent, du plus charge au moins charge. */
    public List<ChargeAgent> charge() {
        LocalDate jour = LocalDate.now();

        Map<Long, List<Ruche>> ruchesParAgent = ruches.findAll().stream()
                .filter(r -> r.getAgentResponsable() != null)
                .collect(Collectors.groupingBy(r -> r.getAgentResponsable().getId()));

        List<Tache> ouvertes = taches.findAll().stream().filter(t -> !t.isFaite()).toList();

        Map<Long, List<Planning>> planningsParAgent =
                plannings.parPeriode(jour, jour.plusDays(HORIZON_JOURS), StatutPlanning.REFUSE)
                        .stream()
                        .collect(Collectors.groupingBy(p -> p.getAgent().getId()));

        return agents.findAll().stream()
                .map(agent -> charge(agent, ruchesParAgent, ouvertes, planningsParAgent, jour))
                // Le plus charge d'abord : c'est la ligne sur laquelle on agit.
                .sorted((a, b) -> Long.compare(
                        b.tachesEnRetard() + b.tachesCritiques(),
                        a.tachesEnRetard() + a.tachesCritiques()))
                .toList();
    }

    private ChargeAgent charge(Agent agent, Map<Long, List<Ruche>> ruchesParAgent,
            List<Tache> ouvertes, Map<Long, List<Planning>> planningsParAgent, LocalDate jour) {

        List<Ruche> siennes = ruchesParAgent.getOrDefault(agent.getId(), List.of());
        // Les ruchers CONCERNES, et non le nombre de ruches : trois ruches sur
        // trois ruchers font trois deplacements.
        Set<Long> ruchers = siennes.stream()
                .filter(r -> r.getSite() != null)
                .map(r -> r.getSite().getId())
                .collect(Collectors.toSet());

        List<Tache> assignees = ouvertes.stream()
                .filter(t -> t.getAgent() != null && t.getAgent().getId().equals(agent.getId()))
                .toList();

        return new ChargeAgent(
                agent.getId(),
                agent.getNom(),
                agent.getRole() == null ? null : agent.getRole().enBase(),
                siennes.size(),
                ruchers.size(),
                assignees.size(),
                assignees.stream()
                        .filter(t -> t.getEcheance() != null && t.getEcheance().isBefore(jour))
                        .count(),
                assignees.stream().filter(t -> "critique".equals(t.getPriorite())).count(),
                planningsParAgent.getOrDefault(agent.getId(), List.of()).size());
    }
}
