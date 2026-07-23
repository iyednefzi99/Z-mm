package com.zumm.controller;

import com.zumm.service.SiteService;
import com.zumm.web.dto.SiteCorps;
import com.zumm.web.dto.SiteReponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
 * API CRUD des sites (US-003), avec recherche de proximite PostGIS.
 */
@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteService service;

    public SiteController(SiteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SiteReponse> creer(@Valid @RequestBody SiteCorps corps) {
        SiteReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/sites/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<SiteReponse> lister() {
        return service.lister();
    }

    /**
     * Sites du tenant a moins de {@code rayonMetres} du point (latitude, longitude).
     * Exemple : {@code GET /api/sites/proches?latitude=45.1&longitude=1.2&rayonMetres=5000}.
     */
    @GetMapping("/proches")
    public List<SiteReponse> proches(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5000") double rayonMetres) {
        return service.proches(latitude, longitude, rayonMetres);
    }

    @GetMapping("/{id}")
    public SiteReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public SiteReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody SiteCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
