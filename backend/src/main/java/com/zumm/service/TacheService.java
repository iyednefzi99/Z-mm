package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Consommable;
import com.zumm.domain.Ruche;
import com.zumm.domain.Tache;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.ConsommableRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.TacheCorps;
import com.zumm.web.dto.TacheReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des taches et rappels de l'apiculteur (US-031). */
@Service
@Transactional
public class TacheService {

    private final TacheRepository taches;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final ConsommableRepository consommables;
    private final NotificationAlerteService notifications;

    public TacheService(TacheRepository taches, RucheRepository ruches, AgentRepository agents,
            ConsommableRepository consommables, NotificationAlerteService notifications) {
        this.taches = taches;
        this.ruches = ruches;
        this.agents = agents;
        this.consommables = consommables;
        this.notifications = notifications;
    }

    public TacheReponse creer(TacheCorps corps) {
        Tache tache = new Tache(corps.libelle());
        appliquer(tache, corps);
        Tache enregistree = taches.save(tache);
        // Seules les taches CRITIQUES partent par courriel : notifier chaque
        // creation reviendrait a n'en notifier aucune, les messages etant filtres
        // des la troisieme semaine.
        if ("critique".equals(enregistree.getPriorite())) {
            notifications.notifierTacheCritique(enregistree);
        }
        return TacheReponse.de(enregistree);
    }

    @Transactional(readOnly = true)
    public List<TacheReponse> lister() {
        return taches.findAll().stream().map(TacheReponse::de).toList();
    }

    /** Page de la liste (US-052). Le total est porte par la Page, pas recompte. */
    @Transactional(readOnly = true)
    public Page<TacheReponse> lister(Pageable pagination) {
        return taches.findAll(pagination).map(TacheReponse::de);
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
        // Priorite absente = `normale`, et non « la plus haute par prudence » :
        // une liste ou tout est urgent ne priorise rien.
        if (corps.priorite() != null) {
            tache.setPriorite(corps.priorite());
        }
        tache.setCategorie(corps.categorie());
        // Ce que la tache consomme (SPRINT-33), pour la feuille de chargement.
        // La quantite SUIT le consommable : la garder alors que celui-ci vient
        // d'etre retire laisserait un nombre sans unite ni objet, que la base
        // refuse (ck_tache_quantite) et que rien ne saurait afficher.
        Consommable consommable = consommableEventuel(corps.consommableId());
        tache.setConsommable(consommable);
        tache.setQuantitePrevue(consommable == null ? null : corps.quantitePrevue());
    }

    private Consommable consommableEventuel(Long id) {
        return id == null ? null : consommables.findById(id).orElseThrow(() ->
                new RequeteInvalide("Consommable inconnu dans ce tenant : " + id));
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
