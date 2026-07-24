package com.zumm.controller;

import com.zumm.service.VisiteService;
import com.zumm.web.dto.PhotoCorps;
import com.zumm.web.dto.PhotoReponse;
import com.zumm.web.dto.VisiteCorps;
import com.zumm.web.dto.VisiteReponse;
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
 * API des visites et de leur rapport (US-009), avec les photos d'inspection en
 * sous-ressource (US-010/028).
 */
@RestController
@RequestMapping("/api/visites")
public class VisiteController {

    private final VisiteService service;

    public VisiteController(VisiteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<VisiteReponse> creer(@Valid @RequestBody VisiteCorps corps) {
        VisiteReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/visites/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<VisiteReponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public VisiteReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public VisiteReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody VisiteCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Photos (sous-ressource) ───────────────────────────────────────────────

    @PostMapping("/{id}/photos")
    public ResponseEntity<PhotoReponse> ajouterPhoto(@PathVariable Long id, @Valid @RequestBody PhotoCorps corps) {
        PhotoReponse reponse = service.ajouterPhoto(id, corps);
        return ResponseEntity
                .created(URI.create("/api/visites/" + id + "/photos/" + reponse.id()))
                .body(reponse);
    }

    @GetMapping("/{id}/photos")
    public List<PhotoReponse> listerPhotos(@PathVariable Long id) {
        return service.listerPhotos(id);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> supprimerPhoto(@PathVariable Long id, @PathVariable Long photoId) {
        service.supprimerPhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }
}
