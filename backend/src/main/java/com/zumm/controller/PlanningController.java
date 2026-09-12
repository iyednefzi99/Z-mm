package com.zumm.controller;

import com.zumm.service.AgendaIcsService;
import com.zumm.service.ChargementService;
import com.zumm.service.PlanningService;
import com.zumm.web.Pagination;
import com.zumm.web.dto.DecisionCorps;
import com.zumm.web.dto.FeuilleChargement;
import com.zumm.web.dto.PlanningCorps;
import com.zumm.web.dto.PlanningReponse;
import com.zumm.web.dto.TourneeReponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API des plannings de visite (US-007) et de leur approbation/refus (US-008).
 */
@RestController
@RequestMapping("/api/plannings")
public class PlanningController {

    private final PlanningService service;
    private final AgendaIcsService agendaIcs;
    private final ChargementService chargements;
    private final Pagination pagination;

    public PlanningController(PlanningService service, AgendaIcsService agendaIcs,
            ChargementService chargements, Pagination pagination) {
        this.service = service;
        this.agendaIcs = agendaIcs;
        this.chargements = chargements;
        this.pagination = pagination;
    }

    @PostMapping
    public ResponseEntity<PlanningReponse> creer(@Valid @RequestBody PlanningCorps corps) {
        PlanningReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/plannings/" + reponse.id())).body(reponse);
    }

    /**
     * Liste, paginee si le client le demande (US-052). Sans {@code page} ni
     * {@code taille}, le comportement est celui d'avant : la liste complete.
     * Le total est toujours porte par l'en-tete {@code X-Total-Count}.
     */
    @GetMapping
    public ResponseEntity<List<PlanningReponse>> lister(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer taille,
            @RequestParam(required = false) String tri) {
        return pagination.reponse(page, taille, tri, service::lister, service::lister);
    }

    /**
     * Ordre de tournee propose a un agent pour une journee (US-047).
     * Exemple : {@code GET /api/plannings/tournee?agentId=3&date=2026-12-04}.
     *
     * <p>L'ordre est une proposition issue d'une heuristique sur des distances a vol
     * d'oiseau : ni optimal, ni routier. Rien ne contraint l'agent a le suivre.
     */
    @GetMapping("/tournee")
    public TourneeReponse tournee(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long departSiteId) {
        return service.tournee(agentId, date, departSiteId);
    }

    /**
     * Feuille de chargement de la tournee (SPRINT-33, lot K).
     *
     * <p>Exemple : {@code GET /api/plannings/chargement?agentId=3&date=2026-04-12}.
     *
     * <p>Ce qu'il faut mettre dans le vehicule avant de partir, dans l'ordre des
     * etapes, plus le total par consommable et ce que le stock n'en couvre pas.
     * Elle NOMME le manque sans le corriger : decider quelle ruche sauter est une
     * decision d'exploitation, elle ne se prend pas dans un calcul.
     */
    @GetMapping("/chargement")
    public FeuilleChargement chargement(
            @RequestParam Long agentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long departSiteId) {
        return chargements.pour(agentId, date, departSiteId);
    }

    /**
     * Visites planifiees au format iCalendar, a importer dans Google Agenda,
     * Outlook ou Apple Calendar (SPRINT-21, §1 de
     * {@code docs/ECART-CONCURRENTS.md}).
     *
     * <p>Exemple : {@code GET /api/plannings/agenda.ics?debut=2026-09-01&fin=2026-09-30}.
     * Sans dates, les trente jours a venir — la fenetre a laquelle on prepare une
     * saison.
     *
     * <p><strong>Un telechargement authentifie, pas une URL d'abonnement.</strong>
     * Un abonnement suppose un jeton permanent dans l'URL, recopie dans les
     * reglages de trois appareils et transmis en clair a chaque intermediaire :
     * un secret de plus, non revocable en pratique. Voir {@code AgendaIcsService}
     * pour l'arbitrage complet, et pour la raison qui exclut toute position du
     * fichier produit.
     */
    @GetMapping(value = "/agenda.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> agenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fin) {
        LocalDate depuis = debut == null ? LocalDate.now() : debut;
        LocalDate jusqua = fin == null ? depuis.plusDays(30) : fin;
        return ResponseEntity.ok()
                // Nom de fichier explicite : un « agenda.ics » anonyme dans le
                // dossier de telechargements ne se retrouve pas.
                .header("Content-Disposition", "attachment; filename=\"zumm-visites.ics\"")
                .body(agendaIcs.calendrier(depuis, jusqua));
    }

    @GetMapping("/{id}")
    public PlanningReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public PlanningReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody PlanningCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @PostMapping("/{id}/approuver")
    public PlanningReponse approuver(@PathVariable Long id) {
        return service.approuver(id);
    }

    @PostMapping("/{id}/refuser")
    public PlanningReponse refuser(@PathVariable Long id, @Valid @RequestBody DecisionCorps decision) {
        return service.refuser(id, decision.motif());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
