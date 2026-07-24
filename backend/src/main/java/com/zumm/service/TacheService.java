package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Ruche;
import com.zumm.domain.Tache;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.TacheCorps;
import com.zumm.web.dto.TacheReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des taches et rappels de l'apiculteur (US-031). */
@Service
@Transactional
public class TacheService {

    private final TacheRepository taches;
    private final RucheRepository ruches;
    private final AgentRepository agents;

    public TacheService(TacheRepository taches, RucheRepository ruches, AgentRepository agents) {
        this.taches = taches;
        this.ruches = ruches;
        this.agents = agents;
    }

    public TacheReponse creer(TacheCorps corps) {
        Tache tache = new Tache(corps.libelle());
        appliquer(tache, corps);
        return TacheReponse.de(taches.save(tache));
    }

    @Transactional(readOnly = true)
    public List<TacheReponse> lister() {
        return taches.findAll().stream().map(TacheReponse::de).toList();
    }

    /** Rappels en cours : taches non faites echues au plus tard aujourd'hui (US-031). */
    @Transactional(readOnly = true)
    public List<TacheReponse> rappels() {
        return taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(LocalDate.now())
                .stream().map(TacheReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public TacheReponse obtenir(Long id) {
        return TacheReponse.de(entite(id));
    }

    public TacheReponse mettreAJour(Long id, TacheCorps corps) {
        Tache tache = entite(id);
        tache.setLibelle(corps.libelle());
        appliquer(tache, corps);
        return TacheReponse.de(tache);
    }

    public void supprimer(Long id) {
        taches.delete(entite(id));
    }

    private void appliquer(Tache tache, TacheCorps corps) {
        tache.setRuche(rucheEventuelle(corps.rucheId()));
        tache.setAgent(agentEventuel(corps.agentId()));
        tache.setEcheance(corps.echeance());
        tache.setFaite(corps.faite());
    }

    private Tache entite(Long id) {
        return taches.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Tache", id));
    }

    private Ruche rucheEventuelle(Long id) {
        return id == null ? null : ruches.findById(id).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + id));
    }

    private Agent agentEventuel(Long id) {
        return id == null ? null : agents.findById(id).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + id));
    }
}
