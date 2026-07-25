package com.zumm.controller;

import com.zumm.service.RucheService;
import com.zumm.web.Pagination;
import com.zumm.web.dto.RucheCorps;
import com.zumm.web.dto.RucheReponse;
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
 * API CRUD des ruches et de leur composition (US-004).
 */
@RestController
@RequestMapping("/api/ruches")
public class RucheController {

    private final RucheService service;
    private final Pagination pagination;

    public RucheController(RucheService service, Pagination pagination) {
        this.service = service;
        this.pagination = pagination;
    }

    @PostMapping
    public ResponseEntity<RucheReponse> creer(@Valid @RequestBody RucheCorps corps) {
        RucheReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/ruches/" + reponse.id())).body(reponse);
    }

    /**
     * Liste, paginee si le client le demande (US-052). Sans {@code page} ni
     * {@code taille}, le comportement est celui d'avant : la liste complete.
     * Le total est toujours porte par l'en-tete {@code X-Total-Count}.
     */
    @GetMapping
    public ResponseEntity<List<RucheReponse>> lister(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer taille,
            @RequestParam(required = false) String tri) {
        return pagination.reponse(page, taille, tri, service::lister, service::lister);
    }

    @GetMapping("/{id}")
    public RucheReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public RucheReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody RucheCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
