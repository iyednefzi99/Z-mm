package com.zumm.controller;

import com.zumm.service.SyntheseService;
import com.zumm.service.TableauDeBordService;
import com.zumm.web.dto.AlerteSanitaire;
import com.zumm.web.dto.CalendrierCellule;
import com.zumm.web.dto.LigneProduction;
import com.zumm.web.dto.SyntheseReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tableaux de bord de pilotage (SPRINT-05) : calendrier matriciel agents × ruches
 * (US-012), production (US-013) et alertes sanitaires (US-014). Lecture seule,
 * ouverte a tout role authentifie.
 */
@RestController
@RequestMapping("/api/tableaux")
public class TableauDeBordController {

    private final TableauDeBordService service;
    private final SyntheseService synthese;

    public TableauDeBordController(TableauDeBordService service, SyntheseService synthese) {
        this.service = service;
        this.synthese = synthese;
    }

    /** US-012 : {@code GET /api/tableaux/calendrier?debut=2026-09-01&fin=2026-09-30}. */
    @GetMapping("/calendrier")
    public List<CalendrierCellule> calendrier(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fin) {
        return service.calendrier(debut, fin);
    }

    /** US-013 : synthese production (poids par ruche + productivite moyenne). */
    @GetMapping("/production")
    public List<LigneProduction> production() {
        return service.production();
    }

    /** US-014 : alertes sanitaires par ruche, critiques d'abord. */
    @GetMapping("/alertes-sanitaires")
    public List<AlerteSanitaire> alertesSanitaires() {
        return service.alertesSanitaires();
    }

    /** US-015 : synthese de pilotage et ROI. */
    @GetMapping("/synthese")
    public SyntheseReponse synthese() {
        return synthese.synthese();
    }
}
