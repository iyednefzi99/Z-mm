package com.zumm.controller;

import com.zumm.service.TacheService;
import com.zumm.web.dto.TacheCorps;
import com.zumm.web.dto.TacheReponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * API des taches et rappels (US-031). {@code GET /api/taches/rappels} renvoie les
 * taches non faites deja echues, pour le calendrier de rappels.
 */
@RestController
@RequestMapping("/api/taches")
public class TacheController {

    private final TacheService service;

    public TacheController(TacheService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TacheReponse> creer(@Valid @RequestBody TacheCorps corps) {
        TacheReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/taches/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<TacheReponse> lister() {
        return service.lister();
    }

    @GetMapping("/rappels")
    public List<TacheReponse> rappels() {
        return service.rappels();
    }

    @GetMapping("/{id}")
    public TacheReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public TacheReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody TacheCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
