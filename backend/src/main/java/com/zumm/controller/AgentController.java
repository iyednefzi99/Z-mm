package com.zumm.controller;

import com.zumm.service.AgentService;
import com.zumm.web.dto.AgentCorps;
import com.zumm.web.dto.AgentReponse;
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
 * API CRUD des agents (US-005), avec role metier.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AgentReponse> creer(@Valid @RequestBody AgentCorps corps) {
        AgentReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/agents/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<AgentReponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public AgentReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public AgentReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody AgentCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
