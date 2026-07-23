package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Ferme;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.AgentCorps;
import com.zumm.web.dto.AgentReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations metier sur les agents (US-005), dotes d'un role et, en option,
 * rattaches a une ferme du tenant. Les DTO sont construits dans la transaction.
 */
@Service
@Transactional
public class AgentService {

    private final AgentRepository agents;
    private final FermeRepository fermes;

    public AgentService(AgentRepository agents, FermeRepository fermes) {
        this.agents = agents;
        this.fermes = fermes;
    }

    public AgentReponse creer(AgentCorps corps) {
        return AgentReponse.de(agents.save(
                new Agent(corps.nom(), corps.role(), fermeEventuelle(corps.fermeId()))));
    }

    @Transactional(readOnly = true)
    public List<AgentReponse> lister() {
        return agents.findAll().stream().map(AgentReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public AgentReponse obtenir(Long id) {
        return AgentReponse.de(entite(id));
    }

    public AgentReponse mettreAJour(Long id, AgentCorps corps) {
        Agent agent = entite(id);
        agent.setNom(corps.nom());
        agent.setRole(corps.role());
        agent.setFerme(fermeEventuelle(corps.fermeId()));
        return AgentReponse.de(agent);
    }

    public void supprimer(Long id) {
        agents.delete(entite(id));
    }

    Agent entite(Long id) {
        return agents.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Agent", id));
    }

    /** Resout la ferme si un identifiant est fourni ; sinon, agent sans ferme. */
    private Ferme fermeEventuelle(Long fermeId) {
        if (fermeId == null) {
            return null;
        }
        return fermes.findById(fermeId).orElseThrow(() ->
                new RequeteInvalide("Ferme inconnue dans ce tenant : " + fermeId));
    }
}
