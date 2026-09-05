package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.ObservationPathologie;
import com.zumm.domain.Photo;
import com.zumm.domain.Planning;
import com.zumm.domain.PointObservation;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.ReleveObservation;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.ObservationPathologieRepository;
import com.zumm.repository.PhotoRepository;
import com.zumm.repository.PlanningRepository;
import com.zumm.repository.PointObservationRepository;
import com.zumm.repository.ReleveObservationRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.ConflitVersion;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MeteoVisite;
import com.zumm.web.dto.ObservationVisite;
import com.zumm.web.dto.PathologieCorps;
import com.zumm.web.dto.PhotoCorps;
import com.zumm.web.dto.PhotoReponse;
import com.zumm.web.dto.PointReleve;
import com.zumm.web.dto.VisiteCorps;
import com.zumm.web.dto.VisiteReponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Realisation des visites, rapports (US-009) et photos d'inspection (US-010/028).
 *
 * <p>Depuis le SPRINT-20, la visite porte aussi la grille d'inspection
 * structuree, la meteo figee et les pathologies constatees. Les trois sont
 * facultatives : une visite eclair n'en remplit aucune, et exiger la grille
 * ferait sauter la saisie plutot que la completer.
 *
 * <p>Le SPRINT-28 y ajoute les releves du carnet parametrable : les points que
 * l'exploitation a choisis dans le referentiel ferme, en plus des onze colonnes
 * du noyau — qui, elles, restent des colonnes.
 */
@Service
@Transactional
public class VisiteService {

    private final VisiteRepository visites;
    private final PhotoRepository photos;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final PlanningRepository plannings;
    private final ObservationPathologieRepository pathologies;
    private final ReleveObservationRepository releves;
    private final PointObservationRepository points;

    public VisiteService(VisiteRepository visites, PhotoRepository photos, RucheRepository ruches,
            AgentRepository agents, PlanningRepository plannings,
            ObservationPathologieRepository pathologies, ReleveObservationRepository releves,
            PointObservationRepository points) {
        this.visites = visites;
        this.photos = photos;
        this.ruches = ruches;
        this.agents = agents;
        this.plannings = plannings;
        this.pathologies = pathologies;
        this.releves = releves;
        this.points = points;
    }

    public VisiteReponse creer(VisiteCorps corps) {
        Visite visite = new Visite(
                rucheRequise(corps.rucheId()),
                agentRequis(corps.agentId()),
                corps.dateVisite(),
                corps.raison() == null ? RaisonVisite.CONTROLE : corps.raison());
        appliquer(visite, corps);
        Visite enregistree = visites.save(visite);
        return VisiteReponse.de(enregistree, List.of(),
                remplacerPathologies(enregistree, corps.pathologiesOuVide()),
                remplacerReleves(enregistree, corps.pointsOuVide()));
    }

    @Transactional(readOnly = true)
    public List<VisiteReponse> lister() {
        return visites.findAll().stream()
                .map(v -> VisiteReponse.de(v, photos.findByVisiteIdOrderByIdAsc(v.getId()),
                        pathologies.findByVisite_IdOrderByPathologieAsc(v.getId()),
                        releves.findByIdVisiteIdOrderByIdPointCodeAsc(v.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public VisiteReponse obtenir(Long id) {
        Visite v = entite(id);
        return VisiteReponse.de(v, photos.findByVisiteIdOrderByIdAsc(v.getId()),
                pathologies.findByVisite_IdOrderByPathologieAsc(v.getId()),
                releves.findByIdVisiteIdOrderByIdPointCodeAsc(v.getId()));
    }

    /**
     * Modifie une visite, en refusant d'ecraser une version plus recente.
     *
     * <p>{@code versionAttendue} est le {@code majLe} que l'appelant avait sous
     * les yeux, ou {@code null} s'il n'en avait pas. Nul, la garde ne joue pas :
     * les ecrans en ligne modifient ce qu'ils viennent de lire, et leur imposer
     * un en-tete supplementaire n'aurait protege personne. C'est le rejeu de la
     * file HORS LIGNE qui en a besoin — une saisie redescendue du rucher a
     * plusieurs heures de retard, et deux agents peuvent l'avoir faite.
     *
     * <p>La comparaison porte sur l'instant a la SECONDE. PostgreSQL rend un
     * {@code timestamptz} a la microseconde, que JSON ne restitue pas toujours
     * a l'identique : comparer les instants bruts produirait des conflits
     * fantomes sur une valeur pourtant relue telle quelle.
     */
    public VisiteReponse mettreAJour(Long id, VisiteCorps corps, Instant versionAttendue) {
        Visite visite = entite(id);
        if (versionAttendue != null && visite.getMajLe() != null
                && visite.getMajLe().truncatedTo(ChronoUnit.SECONDS)
                        .isAfter(versionAttendue.truncatedTo(ChronoUnit.SECONDS))) {
            throw new ConflitVersion("visite", id, visite.getMajLe());
        }
        visite.setRuche(rucheRequise(corps.rucheId()));
        visite.setAgent(agentRequis(corps.agentId()));
        appliquer(visite, corps);
        return VisiteReponse.de(visite, photos.findByVisiteIdOrderByIdAsc(id),
                remplacerPathologies(visite, corps.pathologiesOuVide()),
                remplacerReleves(visite, corps.pointsOuVide()));
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

        // Les deux blocs du SPRINT-20. `null` EFFACE, et c'est voulu : le corps
        // decrit l'etat complet de la visite apres la requete, comme le font
        // deja `constatations` et les autres champs de rapport. Une mise a jour
        // qui omettrait la grille ne doit pas conserver l'ancienne — elle serait
        // alors attribuee a une inspection qui ne l'a pas faite.
        if (corps.observation() == null) {
            new ObservationVisite(null, null, null, null, null, null, null, null, null, null, null)
                    .appliquerA(visite);
        } else {
            corps.observation().appliquerA(visite);
        }
        if (corps.meteo() == null) {
            new MeteoVisite(null, null, null, null).appliquerA(visite);
        } else {
            corps.meteo().appliquerA(visite);
        }
    }

    /**
     * Reecrit la liste des pathologies d'une visite.
     *
     * <p>Remplacement complet plutot que fusion : la contrainte
     * {@code uq_pathologie_visite} interdit deja le doublon, et une fusion
     * rendrait impossible de RETIRER une pathologie saisie par erreur — or une
     * suspicion infirmee doit pouvoir disparaitre, sinon la statistique
     * sanitaire ne redescend jamais.
     */
    private List<ObservationPathologie> remplacerPathologies(
            Visite visite, List<PathologieCorps> demandees) {
        pathologies.deleteAll(pathologies.findByVisite_IdOrderByPathologieAsc(visite.getId()));
        if (demandees.isEmpty()) {
            return List.of();
        }
        List<ObservationPathologie> nouvelles = demandees.stream()
                .map(p -> {
                    ObservationPathologie o = new ObservationPathologie(
                            visite, p.pathologie(), p.graviteOuDefaut());
                    o.setNote(p.note());
                    return o;
                })
                .toList();
        return pathologies.saveAll(nouvelles);
    }

    /**
     * Reecrit les releves du carnet parametrable pour une visite (SPRINT-28).
     *
     * <p>Meme regle que pour les pathologies : remplacement complet. Une fusion
     * rendrait impossible de retirer une case cochee par erreur.
     *
     * <p>C'est ici que se verifie la CORRESPONDANCE entre la valeur envoyee et
     * le type du point. La base ne peut pas le faire — le type vit dans une
     * autre table, hors de portee d'un {@code CHECK} — et l'accepter
     * silencieusement produirait des colonnes a moitie remplies dans toute
     * statistique construite ensuite.
     */
    private List<ReleveObservation> remplacerReleves(Visite visite, List<PointReleve> demandes) {
        releves.deleteAll(releves.findByIdVisiteIdOrderByIdPointCodeAsc(visite.getId()));
        if (demandes.isEmpty()) {
            return List.of();
        }
        releves.flush();
        Map<String, PointObservation> connus = points
                .findAllById(demandes.stream().map(PointReleve::code).toList()).stream()
                .collect(Collectors.toMap(PointObservation::getCode,
                        Function.identity()));
        List<ReleveObservation> nouveaux = demandes.stream().distinct().map(demande -> {
            PointObservation point = connus.get(demande.code());
            if (point == null) {
                // Le referentiel est ferme : un code inconnu est une faute de
                // frappe du client, pas une panne du serveur.
                throw new RequeteInvalide("Point d'observation inconnu : " + demande.code());
            }
            if (point.estEchelle()) {
                if (demande.niveau() == null || demande.coche() != null) {
                    throw new RequeteInvalide("Le point « " + point.getLibelle()
                            + " » attend une intensite de 0 a 3, pas une case cochee.");
                }
                return new ReleveObservation(visite.getId(), point.getCode(), null,
                        demande.niveau().shortValue());
            }
            if (demande.coche() == null || demande.niveau() != null) {
                throw new RequeteInvalide("Le point « " + point.getLibelle()
                        + " » attend une case cochee, pas une intensite.");
            }
            return new ReleveObservation(visite.getId(), point.getCode(), demande.coche(), null);
        }).toList();
        return releves.saveAll(nouveaux);
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
