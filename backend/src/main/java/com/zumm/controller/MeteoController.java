package com.zumm.controller;

import com.zumm.service.MeteoService;
import com.zumm.web.dto.MeteoReponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contexte meteo local par site (US-029). {@code GET /api/meteo?siteId=...} —
 * lecture seule, ouverte a tout role authentifie.
 */
@RestController
@RequestMapping("/api/meteo")
public class MeteoController {

    private final MeteoService service;

    public MeteoController(MeteoService service) {
        this.service = service;
    }

    @GetMapping
    public MeteoReponse pourSite(@RequestParam Long siteId) {
        return service.pourSite(siteId);
    }
}
