package com.zumm.controller;

import com.zumm.service.RecolteService;
import com.zumm.web.dto.RecolteCorps;
import com.zumm.web.dto.RecolteReponse;
import com.zumm.web.dto.TraceReponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Recoltes et tracabilite par lot (US-033). La creation genere un numero de lot ;
 * la reponse porte le {@code qrPayload} a encoder cote client. La fiche de
 * tracabilite d'un lot est exposee sur {@code GET /api/recoltes/tracabilite/{lot}}.
 */
@RestController
@RequestMapping("/api/recoltes")
public class RecolteController {

    private final RecolteService service;

    public RecolteController(RecolteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RecolteReponse> creer(@Valid @RequestBody RecolteCorps corps) {
        RecolteReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/recoltes/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<RecolteReponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public RecolteReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /** Fiche de tracabilite scannee depuis le QR code d'un lot. */
    @GetMapping("/tracabilite/{lot}")
    public TraceReponse tracer(@PathVariable String lot) {
        return service.tracer(lot);
    }
}
