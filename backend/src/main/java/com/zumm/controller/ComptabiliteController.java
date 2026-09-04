package com.zumm.controller;

import com.zumm.service.ComptabiliteService;
import com.zumm.web.dto.BilanExploitation;
import com.zumm.web.dto.DepenseCorps;
import com.zumm.web.dto.DepenseReponse;
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

/**
 * Depenses et bilan economique (SPRINT-27, lot E).
 *
 * <p>La frontiere est celle du §9 : rentabilite par ruche, et rien au-dela.
 * Facturation, TVA, devis et clients restent hors perimetre.
 */
@RestController
@RequestMapping("/api/depenses")
public class ComptabiliteController {

    private final ComptabiliteService service;

    public ComptabiliteController(ComptabiliteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<DepenseReponse> creer(@Valid @RequestBody DepenseCorps corps) {
        DepenseReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/depenses/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<DepenseReponse> lister() {
        return service.lister();
    }

    @PutMapping("/{id}")
    public DepenseReponse mettreAJour(@PathVariable Long id,
            @Valid @RequestBody DepenseCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Bilan d'une periode, ventile par categorie et par ruche.
     *
     * <p>La periode est DEMANDEE, jamais devinee : un bilan sur « les douze
     * derniers mois » recule chaque jour, et deux consultations a une semaine
     * d'ecart ne porteraient pas sur la meme chose.
     */
    @GetMapping("/bilan")
    public BilanExploitation bilan(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.bilan(debut, fin);
    }
}
