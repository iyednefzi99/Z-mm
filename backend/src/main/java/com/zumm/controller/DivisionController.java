package com.zumm.controller;

import com.zumm.service.DivisionService;
import com.zumm.web.dto.DivisionCorps;
import com.zumm.web.dto.DivisionReponse;
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
 * Registre des divisions (SPRINT-21).
 *
 * <p>Ressource propre, ouverte a l'apiculteur comme {@code /api/traitements} : il
 * est celui qui divise. La clause terminale de {@code SecurityConfig} suffit donc,
 * et c'est voulu — reserver l'ecriture au responsable ferait saisir l'acte par
 * quelqu'un qui n'y etait pas, ou pas du tout.
 */
@RestController
@RequestMapping("/api/divisions")
public class DivisionController {

    private final DivisionService service;

    public DivisionController(DivisionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DivisionReponse> enregistrer(@Valid @RequestBody DivisionCorps corps) {
        DivisionReponse reponse = service.enregistrer(corps);
        return ResponseEntity.created(URI.create("/api/divisions/" + reponse.id())).body(reponse);
    }

    /** Divisions issues d'une ruche mere. */
    @GetMapping
    public List<DivisionReponse> registre(@RequestParam Long rucheMereId) {
        return service.registre(rucheMereId);
    }

    /** Filiation d'une ruche dans les deux sens : d'ou elle vient, ce qu'elle a donne. */
    @GetMapping("/filiation")
    public List<DivisionReponse> filiation(@RequestParam Long rucheId) {
        return service.filiation(rucheId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
