package com.zumm.controller;

import com.zumm.service.TransportService;
import com.zumm.web.dto.TransportCorps;
import com.zumm.web.dto.TransportReponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plans de transhumance (SPRINT-21).
 *
 * <p>{@code POST /api/transports/&#123;id&#125;/realiser} est la route qui compte : elle
 * fait demenager le rucher, c'est-a-dire qu'elle appelle exactement la meme
 * operation que {@code POST /api/sites/&#123;id&#125;/demenagement}. Deux chemins, une
 * seule regle, un seul historique.
 */
@RestController
@RequestMapping("/api/transports")
public class TransportController {

    private final TransportService service;

    public TransportController(TransportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransportReponse> planifier(@Valid @RequestBody TransportCorps corps) {
        TransportReponse reponse = service.planifier(corps);
        return ResponseEntity.created(URI.create("/api/transports/" + reponse.id())).body(reponse);
    }

    /**
     * Sans {@code siteId}, les transports encore prevus de l'exploitation ; avec,
     * l'historique complet d'un rucher.
     */
    @GetMapping
    public List<TransportReponse> lister(
            @RequestParam(required = false) Long siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fin) {
        return siteId == null ? service.prevus(debut, fin) : service.parSite(siteId);
    }

    @PostMapping("/{id}/realiser")
    public TransportReponse realiser(@PathVariable Long id) {
        return service.realiser(id);
    }

    @PostMapping("/{id}/annuler")
    public TransportReponse annuler(@PathVariable Long id) {
        return service.annuler(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
