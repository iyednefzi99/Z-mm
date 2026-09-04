package com.zumm.controller;

import com.zumm.service.BrouillonVisiteService;
import com.zumm.web.dto.BrouillonCorps;
import com.zumm.web.dto.BrouillonReponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Brouillons de visite (SPRINT-24, lot C).
 *
 * <p>{@code PUT} et non {@code POST} : deposer un brouillon est une operation
 * IDEMPOTENTE sur la paire (agent, ruche). Un {@code POST} rendrait 201 a chaque
 * frappe sauvegardee et laisserait croire a une creation, alors qu'il n'y a
 * jamais qu'un brouillon par ruche et par agent.
 */
@RestController
@RequestMapping("/api/brouillons")
public class BrouillonController {

    private final BrouillonVisiteService service;

    public BrouillonController(BrouillonVisiteService service) {
        this.service = service;
    }

    @PutMapping
    public BrouillonReponse deposer(@Valid @RequestBody BrouillonCorps corps) {
        return service.deposer(corps);
    }

    /** Les brouillons d'un agent, du plus recent au plus ancien. */
    @GetMapping
    public List<BrouillonReponse> lister(@RequestParam Long agentId) {
        return service.mesBrouillons(agentId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> effacer(@PathVariable Long id) {
        service.effacer(id);
        return ResponseEntity.noContent().build();
    }
}
