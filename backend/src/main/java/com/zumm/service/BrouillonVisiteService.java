package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.BrouillonVisite;
import com.zumm.domain.Ruche;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.BrouillonVisiteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.BrouillonCorps;
import com.zumm.web.dto.BrouillonReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brouillons de visite reprenables d'un appareil a l'autre (SPRINT-24, lot C).
 *
 * <p>Le service tient en trois gestes — deposer, lister, effacer — et l'essentiel
 * de sa valeur est dans ce qu'il NE fait pas :
 *
 * <ul>
 *   <li>il ne <strong>valide pas</strong> le contenu. Une saisie en cours a le
 *       droit d'etre incomplete ; la refuser tant qu'elle ne l'est pas ferait
 *       perdre exactement ce qu'on cherche a sauver ;
 *   <li>il ne <strong>transforme pas</strong> un brouillon en visite. C'est le
 *       front qui repose le formulaire et appelle {@code POST /api/visites} —
 *       la visite reste creee par le chemin habituel, avec ses validations ;
 *   <li>il n'<strong>archive pas</strong>. Un brouillon repris est efface : le
 *       garder ferait reapparaitre demain une saisie deja versee au registre.
 * </ul>
 */
@Service
@Transactional
public class BrouillonVisiteService {

    private final BrouillonVisiteRepository brouillons;
    private final AgentRepository agents;
    private final RucheRepository ruches;

    public BrouillonVisiteService(BrouillonVisiteRepository brouillons, AgentRepository agents,
            RucheRepository ruches) {
        this.brouillons = brouillons;
        this.agents = agents;
        this.ruches = ruches;
    }

    /**
     * Depose un brouillon, ou remplace celui qui existe deja sur cette ruche.
     *
     * <p>Le dernier appareil qui ecrit gagne, et c'est le comportement voulu :
     * le besoin nomme est de continuer une saisie, pas d'en tenir deux versions.
     * {@code appareil} dit d'ou vient la derniere ecriture, pour que l'agent
     * reconnaisse la sienne.
     */
    public BrouillonReponse deposer(BrouillonCorps corps) {
        Agent agent = agents.findById(corps.agentId())
                .orElseThrow(() -> RessourceIntrouvable.de("Agent", corps.agentId()));
        Ruche ruche = ruches.findById(corps.rucheId())
                .orElseThrow(() -> RessourceIntrouvable.de("Ruche", corps.rucheId()));

        BrouillonVisite brouillon = brouillons
                .findByAgent_IdAndRuche_Id(agent.getId(), ruche.getId())
                .orElseGet(() -> new BrouillonVisite(agent, ruche, corps.contenu(),
                        corps.appareil()));
        brouillon.setContenu(corps.contenu());
        brouillon.setAppareil(corps.appareil());
        return BrouillonReponse.de(brouillons.save(brouillon));
    }

    @Transactional(readOnly = true)
    public List<BrouillonReponse> mesBrouillons(Long agentId) {
        return brouillons.findByAgent_IdOrderByMajLeDesc(agentId).stream()
                .map(BrouillonReponse::de)
                .toList();
    }

    public void effacer(Long id) {
        BrouillonVisite brouillon = brouillons.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Brouillon", id));
        brouillons.delete(brouillon);
    }
}
