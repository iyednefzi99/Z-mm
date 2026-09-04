package com.zumm.controller;

import com.zumm.service.CorrelationMeteoService;
import com.zumm.web.dto.CorrelationMeteo;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correlations meteo / production (SPRINT-22, lot A).
 *
 * <p>Sans dates, les douze derniers mois : une correlation saisonniere n'a de
 * sens que sur au moins un cycle complet, et une fenetre plus courte ne
 * comparerait qu'un printemps a lui-meme.
 */
@RestController
@RequestMapping("/api/correlations")
public class CorrelationController {

    private final CorrelationMeteoService service;

    public CorrelationController(CorrelationMeteoService service) {
        this.service = service;
    }

    @GetMapping("/meteo")
    public List<CorrelationMeteo> meteo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fin) {
        LocalDate jusqua = fin == null ? LocalDate.now() : fin;
        LocalDate depuis = debut == null ? jusqua.minusYears(1) : debut;
        return service.calculer(depuis, jusqua);
    }
}
