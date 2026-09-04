package com.zumm.controller;

import com.zumm.service.StockService;
import com.zumm.web.dto.ConsommableCorps;
import com.zumm.web.dto.ConsommableReponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
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

/** Stock de consommables et seuils de reapprovisionnement (SPRINT-27, lot E). */
@RestController
@RequestMapping("/api/consommables")
public class ConsommableController {

    private final StockService service;

    public ConsommableController(StockService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ConsommableReponse> creer(@Valid @RequestBody ConsommableCorps corps) {
        ConsommableReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/consommables/" + reponse.id()))
                .body(reponse);
    }

    @GetMapping
    public List<ConsommableReponse> lister() {
        return service.lister();
    }

    @PutMapping("/{id}")
    public ConsommableReponse mettreAJour(@PathVariable Long id,
            @Valid @RequestBody ConsommableCorps corps) {
        return service.mettreAJour(id, corps);
    }

    /**
     * Entree ou sortie de stock. `delta` POSITIF ajoute, NEGATIF retire.
     *
     * <p>Un mouvement plutot qu'un total : deux personnes qui prelevent du candi
     * le meme jour ne s'ecrasent pas l'une l'autre, alors qu'une saisie « il
     * reste 12 kg » ecrase sans que personne ne le voie.
     */
    @PostMapping("/{id}/mouvement")
    public ConsommableReponse mouvementer(@PathVariable Long id, @RequestParam BigDecimal delta) {
        return service.mouvementer(id, delta);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
