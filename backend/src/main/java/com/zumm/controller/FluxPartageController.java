package com.zumm.controller;

import com.zumm.domain.TypeIndicateur;
import com.zumm.service.FluxPartageService;
import com.zumm.web.dto.FluxPartage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flux de telemetrie partage, lu SANS session (SPRINT-26, lot F1).
 *
 * <p>Seconde et derniere route publique du produit, apres le flux iCalendar du
 * SPRINT-21. Memes garanties : jeton de 256 bits jamais stocke en clair,
 * expiration obligatoire, revocation, usage horodate — et la meme faiblesse,
 * qu'il faut redire : <strong>aucune limitation de debit</strong> sur ce chemin.
 * A prevoir si l'application s'ouvre largement.
 *
 * <p>Prefixe SEPARE de {@code /api/partages}, qui porte la gestion :
 * {@code TenantFilter} exempte ses chemins publics par prefixe, et les melanger
 * aurait exempte la revocation du meme coup.
 */
@RestController
public class FluxPartageController {

    private final FluxPartageService service;

    public FluxPartageController(FluxPartageService service) {
        this.service = service;
    }

    /**
     * 404 dans TOUS les cas de refus — jeton inconnu, revoque, expire.
     * Distinguer les trois confirmerait qu'un jeton a existe, ce qui est deja
     * une information de trop.
     */
    @GetMapping("/api/flux/{jeton}")
    public ResponseEntity<FluxPartage> consulter(@PathVariable String jeton,
            @RequestParam(defaultValue = "poids") TypeIndicateur indicateur) {
        return service.flux(jeton, indicateur)
                .map(flux -> ResponseEntity.ok()
                        // Rien ne doit rester chez un intermediaire : un partage
                        // revoque cesse de repondre tout de suite, pas quand un
                        // cache voudra bien expirer.
                        .header("Cache-Control", "no-store")
                        .body(flux))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
