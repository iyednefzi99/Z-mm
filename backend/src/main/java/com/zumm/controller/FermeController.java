package com.zumm.controller;

import com.zumm.service.FermeService;
import com.zumm.web.dto.FermeCorps;
import com.zumm.web.dto.FermeReponse;
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
 * API CRUD des fermes (US-002), rattachees a un fermier du meme tenant.
 */
@RestController
@RequestMapping("/api/fermes")
public class FermeController {

    private final FermeService service;

    public FermeController(FermeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FermeReponse> creer(@Valid @RequestBody FermeCorps corps) {
        FermeReponse reponse = FermeReponse.de(service.creer(corps));
        return ResponseEntity.created(URI.create("/api/fermes/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<FermeReponse> lister() {
        return service.lister().stream().map(FermeReponse::de).toList();
    }

    @GetMapping("/{id}")
    public FermeReponse obtenir(@PathVariable Long id) {
        return FermeReponse.de(service.obtenir(id));
    }

    @PutMapping("/{id}")
    public FermeReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody FermeCorps corps) {
        return FermeReponse.de(service.mettreAJour(id, corps));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
