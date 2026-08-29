package com.zumm.controller;

import com.zumm.service.ComptageVarroaService;
import com.zumm.web.dto.ComptageVarroaCorps;
import com.zumm.web.dto.ComptageVarroaReponse;
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
 * Comptages de varroa (SPRINT-20).
 *
 * <p>La reponse porte le taux ET son unite, calcules a la lecture : le client
 * n'a pas a refaire l'arithmetique, et surtout pas a la refaire differemment
 * selon la methode.
 */
@RestController
@RequestMapping("/api/varroa")
public class ComptageVarroaController {

    private final ComptageVarroaService service;

    public ComptageVarroaController(ComptageVarroaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ComptageVarroaReponse> enregistrer(
            @Valid @RequestBody ComptageVarroaCorps corps) {
        ComptageVarroaReponse reponse = service.enregistrer(corps);
        return ResponseEntity.created(URI.create("/api/varroa/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<ComptageVarroaReponse> serie(@RequestParam Long rucheId) {
        return service.serie(rucheId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
