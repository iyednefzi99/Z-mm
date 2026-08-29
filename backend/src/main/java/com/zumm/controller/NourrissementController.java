package com.zumm.controller;

import com.zumm.service.NourrissementService;
import com.zumm.web.dto.NourrissementCorps;
import com.zumm.web.dto.NourrissementReponse;
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
 * Registre des nourrissements (SPRINT-20). Meme forme et meme ouverture que
 * {@link TraitementController} : c'est l'apiculteur qui nourrit.
 */
@RestController
@RequestMapping("/api/nourrissements")
public class NourrissementController {

    private final NourrissementService service;

    public NourrissementController(NourrissementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<NourrissementReponse> enregistrer(
            @Valid @RequestBody NourrissementCorps corps) {
        NourrissementReponse reponse = service.enregistrer(corps);
        return ResponseEntity.created(URI.create("/api/nourrissements/" + reponse.id()))
                .body(reponse);
    }

    @GetMapping
    public List<NourrissementReponse> registre(@RequestParam Long rucheId) {
        return service.registre(rucheId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
