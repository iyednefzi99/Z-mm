package com.zumm.controller;

import com.zumm.service.CaptureEssaimService;
import com.zumm.web.dto.CaptureEssaimCorps;
import com.zumm.web.dto.CaptureEssaimReponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registre des captures d'essaim (SPRINT-21).
 *
 * <p>{@code POST /api/captures/{id}/loger} est une operation a part parce qu'elle
 * arrive plus tard : on capture un jour, on loge quand la colonie a pris.
 */
@RestController
@RequestMapping("/api/captures")
public class CaptureEssaimController {

    private final CaptureEssaimService service;

    public CaptureEssaimController(CaptureEssaimService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<CaptureEssaimReponse> enregistrer(
            @Valid @RequestBody CaptureEssaimCorps corps) {
        CaptureEssaimReponse reponse = service.enregistrer(corps);
        return ResponseEntity.created(URI.create("/api/captures/" + reponse.id())).body(reponse);
    }

    /**
     * Liste des captures. Avec {@code debut} et {@code fin}, la saison ; avec
     * {@code enAttente=true}, ce qui reste a loger.
     */
    @GetMapping
    public List<CaptureEssaimReponse> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fin,
            @RequestParam(required = false, defaultValue = "false") boolean enAttente) {
        if (enAttente) {
            return service.enAttente();
        }
        return debut != null && fin != null ? service.saison(debut, fin) : service.lister();
    }

    /** Loge une capture dans une ruche : l'essaim devient une colonie du parc. */
    @PostMapping("/{id}/loger")
    public CaptureEssaimReponse loger(@PathVariable Long id, @RequestParam Long rucheId) {
        return service.loger(id, rucheId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
