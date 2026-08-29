package com.zumm.controller;

import com.zumm.service.TraitementService;
import com.zumm.web.dto.TraitementCorps;
import com.zumm.web.dto.TraitementReponse;
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
 * Registre des traitements sanitaires (SPRINT-20).
 *
 * <p>Ressource propre, sur le modele de {@code /api/reines} : elle reste ouverte
 * a l'apiculteur, qui est celui qui traite. La matrice RBAC n'a donc pas de
 * regle propre a ajouter — la clause terminale de {@code SecurityConfig} suffit,
 * et c'est voulu : reserver l'ecriture au responsable obligerait l'homme de
 * terrain a faire saisir son acte par quelqu'un d'autre, ce qui est le meilleur
 * moyen qu'il ne soit pas saisi.
 *
 * <p>{@code GET /api/traitements/carence} est la seule route qui ne prend pas de
 * ruche : elle repond a « que puis-je recolter aujourd'hui », a l'echelle de
 * l'exploitation.
 */
@RestController
@RequestMapping("/api/traitements")
public class TraitementController {

    private final TraitementService service;

    public TraitementController(TraitementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TraitementReponse> enregistrer(
            @Valid @RequestBody TraitementCorps corps) {
        TraitementReponse reponse = service.enregistrer(corps);
        return ResponseEntity.created(URI.create("/api/traitements/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<TraitementReponse> registre(@RequestParam Long rucheId) {
        return service.registre(rucheId);
    }

    /** Traitements dont la carence court encore : ces ruches ne se recoltent pas. */
    @GetMapping("/carence")
    public List<TraitementReponse> sousCarence() {
        return service.sousCarence();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
