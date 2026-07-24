package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Planning;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Ruche;
import com.zumm.domain.StatutPlanning;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.PlanningRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.PlanningCorps;
import com.zumm.web.dto.PlanningReponse;
import java.util.List;
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

    public PlanningService(PlanningRepository plannings, RucheRepository ruches, AgentRepository agents) {
        this.plannings = plannings;
        this.ruches = ruches;
        this.agents = agents;
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
