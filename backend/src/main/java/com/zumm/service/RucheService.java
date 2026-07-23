package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Compartiment;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ferme;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.TypeCompartiment;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.CompartimentCorps;
import com.zumm.web.dto.RucheCorps;
import com.zumm.web.dto.RucheReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations metier sur les ruches et leur composition (US-004).
 *
 * <p>Applique les regles de composition de l'annexe A : exactement un corps,
 * jusqu'a cinq hausses, 1 a 10 cadres par compartiment (ce dernier point validated
 * sur le DTO et par CHECK). « Au plus un corps » est aussi garanti par un index
 * unique partiel en base ; « exactement un corps » et « au plus cinq hausses »
 * sont des regles inter-lignes, verifiees ici.
 */
@Service
@Transactional
public class RucheService {

    private static final int MAX_HAUSSES = 5;

    private final RucheRepository ruches;
    private final SiteRepository sites;
    private final FermeRepository fermes;
    private final AgentRepository agents;

    public RucheService(RucheRepository ruches, SiteRepository sites, FermeRepository fermes,
            AgentRepository agents) {
        this.ruches = ruches;
        this.sites = sites;
        this.fermes = fermes;
        this.agents = agents;
    }

    public RucheReponse creer(RucheCorps corps) {
        verifierComposition(corps.compartiments());
        Ruche ruche = new Ruche(
                corps.modele(),
                siteRequis(corps.siteId()),
                fermeRequise(corps.fermeId()),
                corps.etat() == null ? EtatRuche.CREEE : corps.etat());
        ruche.setAgentResponsable(agentEventuel(corps.agentResponsableId()));
        appliquerComposition(ruche, corps.compartiments());
        return RucheReponse.de(ruches.save(ruche));
    }

    @Transactional(readOnly = true)
    public List<RucheReponse> lister() {
        return ruches.findAll().stream().map(RucheReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public RucheReponse obtenir(Long id) {
        return RucheReponse.de(entite(id));
    }

    public RucheReponse mettreAJour(Long id, RucheCorps corps) {
        verifierComposition(corps.compartiments());
        Ruche ruche = entite(id);
        ruche.setModele(corps.modele());
        ruche.setSite(siteRequis(corps.siteId()));
        ruche.setFerme(fermeRequise(corps.fermeId()));
        ruche.setAgentResponsable(agentEventuel(corps.agentResponsableId()));
        if (corps.etat() != null) {
            ruche.setEtat(corps.etat());
        }
        // Supprimer l'ancienne composition AVANT d'inserer la nouvelle, pour ne pas
        // heurter l'index unique partiel sur le corps pendant le meme flush.
        ruche.viderCompartiments();
        ruches.flush();
        appliquerComposition(ruche, corps.compartiments());
        return RucheReponse.de(ruche);
    }

    public void supprimer(Long id) {
        ruches.delete(entite(id));
    }

    Ruche entite(Long id) {
        return ruches.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Ruche", id));
    }

    private void appliquerComposition(Ruche ruche, List<CompartimentCorps> compartiments) {
        for (CompartimentCorps c : compartiments) {
            ruche.ajouterCompartiment(new Compartiment(c.type(), c.nbCadres()));
        }
    }

    /** Regles de cardinalite de la composition (annexe A). */
    private void verifierComposition(List<CompartimentCorps> compartiments) {
        long corps = compartiments.stream().filter(c -> c.type() == TypeCompartiment.CORPS).count();
        long hausses = compartiments.stream().filter(c -> c.type() == TypeCompartiment.HAUSSE).count();
        if (corps != 1) {
            throw new RequeteInvalide("Une ruche doit comporter exactement un corps.");
        }
        if (hausses > MAX_HAUSSES) {
            throw new RequeteInvalide("Une ruche ne peut compter plus de cinq hausses.");
        }
    }

    private Site siteRequis(Long siteId) {
        return sites.findById(siteId).orElseThrow(() ->
                new RequeteInvalide("Site inconnu dans ce tenant : " + siteId));
    }

    private Ferme fermeRequise(Long fermeId) {
        return fermes.findById(fermeId).orElseThrow(() ->
                new RequeteInvalide("Ferme inconnue dans ce tenant : " + fermeId));
    }

    private Agent agentEventuel(Long agentId) {
        if (agentId == null) {
            return null;
        }
        return agents.findById(agentId).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + agentId));
    }
}
