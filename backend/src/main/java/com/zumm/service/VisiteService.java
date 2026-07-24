package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Photo;
import com.zumm.domain.Planning;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.PhotoRepository;
import com.zumm.repository.PlanningRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.PhotoCorps;
import com.zumm.web.dto.PhotoReponse;
import com.zumm.web.dto.VisiteCorps;
import com.zumm.web.dto.VisiteReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Realisation des visites, rapports (US-009) et photos d'inspection (US-010/028).
 */
@Service
@Transactional
public class VisiteService {

    private final VisiteRepository visites;
    private final PhotoRepository photos;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final PlanningRepository plannings;

    public VisiteService(VisiteRepository visites, PhotoRepository photos, RucheRepository ruches,
            AgentRepository agents, PlanningRepository plannings) {
        this.visites = visites;
        this.photos = photos;
        this.ruches = ruches;
        this.agents = agents;
        this.plannings = plannings;
    }

    public VisiteReponse creer(VisiteCorps corps) {
        Visite visite = new Visite(
                rucheRequise(corps.rucheId()),
                agentRequis(corps.agentId()),
                corps.dateVisite(),
                corps.raison() == null ? RaisonVisite.CONTROLE : corps.raison());
        appliquer(visite, corps);
        return VisiteReponse.de(visites.save(visite), List.of());
    }

    @Transactional(readOnly = true)
    public List<VisiteReponse> lister() {
        return visites.findAll().stream()
                .map(v -> VisiteReponse.de(v, photos.findByVisiteIdOrderByIdAsc(v.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public VisiteReponse obtenir(Long id) {
        Visite v = entite(id);
        return VisiteReponse.de(v, photos.findByVisiteIdOrderByIdAsc(v.getId()));
    }

    public VisiteReponse mettreAJour(Long id, VisiteCorps corps) {
        Visite visite = entite(id);
        visite.setRuche(rucheRequise(corps.rucheId()));
        visite.setAgent(agentRequis(corps.agentId()));
        appliquer(visite, corps);
        return VisiteReponse.de(visite, photos.findByVisiteIdOrderByIdAsc(id));
    }

    public void supprimer(Long id) {
        visites.delete(entite(id));
    }

    // ─── Photos ──────────────────────────────────────────────────────────────

    public PhotoReponse ajouterPhoto(Long visiteId, PhotoCorps corps) {
        Visite visite = entite(visiteId);
        return PhotoReponse.de(photos.save(new Photo(visite, corps.url(), corps.legende())));
    }

    @Transactional(readOnly = true)
    public List<PhotoReponse> listerPhotos(Long visiteId) {
        entite(visiteId); // 404 si la visite n'existe pas dans le tenant.
        return photos.findByVisiteIdOrderByIdAsc(visiteId).stream().map(PhotoReponse::de).toList();
    }

    public void supprimerPhoto(Long visiteId, Long photoId) {
        Photo photo = photos.findById(photoId)
                .orElseThrow(() -> RessourceIntrouvable.de("Photo", photoId));
        if (!photo.getVisite().getId().equals(visiteId)) {
            throw new RequeteInvalide("Cette photo n'appartient pas à la visite " + visiteId + ".");
        }
        photos.delete(photo);
    }

    private void appliquer(Visite visite, VisiteCorps corps) {
        visite.setPlanning(planningEventuel(corps.planningId()));
        visite.setDateVisite(corps.dateVisite());
        visite.setHeureVisite(corps.heureVisite());
        visite.setDureeMin(corps.dureeMin());
        if (corps.raison() != null) {
            visite.setRaison(corps.raison());
        }
        visite.setConstatations(corps.constatations());
        visite.setActionsPrevues(corps.actionsPrevues());
        visite.setActionsEffectuees(corps.actionsEffectuees());
        visite.setRecommandations(corps.recommandations());
        visite.setEffectifQualitatif(corps.effectifQualitatif());
        visite.setEtatSante(corps.etatSante());
        visite.setProductivite(corps.productivite());
    }

    Visite entite(Long id) {
        return visites.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Visite", id));
    }

    private Ruche rucheRequise(Long id) {
        return ruches.findById(id).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + id));
    }

    private Agent agentRequis(Long id) {
        return agents.findById(id).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + id));
    }

    private Planning planningEventuel(Long id) {
        return id == null ? null : plannings.findById(id).orElseThrow(() ->
                new RequeteInvalide("Planning inconnu dans ce tenant : " + id));
    }
}
