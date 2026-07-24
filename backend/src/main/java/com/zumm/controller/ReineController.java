package com.zumm.controller;

import com.zumm.service.SuiviReineService;
import com.zumm.web.dto.ReineCorps;
import com.zumm.web.dto.ReineReponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * Suivi de la reine (US-032). Ressource propre (hors {@code /api/ruches}) pour
 * rester ouverte a l'apiculteur : {@code GET /api/reines?rucheId=...} liste
 * l'historique d'une ruche, {@code POST} enregistre un evenement.
 */
@RestController
@RequestMapping("/api/reines")
public class ReineController {

    private final SuiviReineService service;

    public ReineController(SuiviReineService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReineReponse> enregistrer(@Valid @RequestBody ReineCorps corps) {
        ReineReponse reponse = service.enregistrer(corps);
        return ResponseEntity.created(URI.create("/api/reines/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<ReineReponse> historique(@RequestParam Long rucheId) {
        return service.historique(rucheId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
