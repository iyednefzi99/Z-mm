package com.zumm.controller;

import com.zumm.service.MaterielService;
import com.zumm.web.dto.MaterielCorps;
import com.zumm.web.dto.MaterielReponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Inventaire et entretien du materiel (SPRINT-27, lot E). */
@RestController
@RequestMapping("/api/materiels")
public class MaterielController {

    private final MaterielService service;

    public MaterielController(MaterielService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MaterielReponse> creer(@Valid @RequestBody MaterielCorps corps) {
        MaterielReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/materiels/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<MaterielReponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public MaterielReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public MaterielReponse mettreAJour(@PathVariable Long id,
            @Valid @RequestBody MaterielCorps corps) {
        return service.mettreAJour(id, corps);
    }

    /**
     * Marque l'entretien fait : un geste dedie plutot qu'une modification.
     *
     * <p>C'est l'action reelle — « je viens de reviser l'extracteur » — et elle
     * repousse l'echeance sans rien d'autre a ressaisir.
     */
    @PostMapping("/{id}/entretien")
    public MaterielReponse entretenir(@PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        return service.entretenir(id, jour);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
