package com.zumm.controller;

import com.zumm.service.PlanningService;
import com.zumm.web.dto.DecisionCorps;
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

    public PlanningController(PlanningService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PlanningReponse> creer(@Valid @RequestBody PlanningCorps corps) {
        PlanningReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/plannings/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<PlanningReponse> lister() {
        return service.lister();
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
    public PlanningReponse refuser(@PathVariable Long id, @RequestBody DecisionCorps decision) {
        return service.refuser(id, decision.motif());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
