package com.zumm.controller;

import com.zumm.service.RechercheService;
import com.zumm.web.dto.ResultatRecherche;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recherche transverse (SPRINT-21).
 *
 * <p>Une seule route, en lecture seule. Le cloisonnement n'est pas ecrit ici : la
 * RLS et la portee d'agent (V16) s'appliquent a chaque requete du service, si
 * bien qu'un saisonnier ne trouve que ce qui lui est affecte.
 */
@RestController
@RequestMapping("/api/recherche")
public class RechercheController {

    private final RechercheService service;

    public RechercheController(RechercheService service) {
        this.service = service;
    }

    /** Exemple : {@code GET /api/recherche?q=tilleul&limite=10}. */
    @GetMapping
    public List<ResultatRecherche> rechercher(@RequestParam String q,
            @RequestParam(required = false) Integer limite) {
        return service.rechercher(q, limite);
    }
}
