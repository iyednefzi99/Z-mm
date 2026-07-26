package com.zumm.controller;

import com.zumm.service.LotConditionnementService;
import com.zumm.web.dto.LotCorps;
import com.zumm.web.dto.LotReponse;
import com.zumm.web.dto.MentionOrigine;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

/**
 * Lots de conditionnement et mention d'origine (US-056).
 *
 * <p>{@code GET /api/lots/{id}/mention} rend la mention exigee par la directive
 * (UE) 2024/1438 dans la langue negociee (en-tete {@code Accept-Language}) : une
 * etiquette destinee a un marche etranger s'imprime dans la langue de ce marche.
 */
@RestController
@RequestMapping("/api/lots")
public class LotController {

    private final LotConditionnementService service;

    public LotController(LotConditionnementService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LotReponse> creer(@Valid @RequestBody LotCorps corps) {
        LotReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/lots/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<LotReponse> lister() {
        return service.lister();
    }

    @GetMapping("/{id}")
    public LotReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PutMapping("/{id}")
    public LotReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody LotCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void supprimer(@PathVariable Long id) {
        service.supprimer(id);
    }

    /** Mention d'origine prete a imprimer (directive (UE) 2024/1438). */
    @GetMapping("/{id}/mention")
    public MentionOrigine mention(@PathVariable Long id, Locale locale) {
        return service.mention(id, locale);
    }
}
