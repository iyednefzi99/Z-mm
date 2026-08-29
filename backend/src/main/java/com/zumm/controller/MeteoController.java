package com.zumm.controller;

import com.zumm.service.MeteoService;
import com.zumm.web.dto.MeteoReponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contexte meteo local par site (US-029).
 * {@code GET /api/meteo?siteId=...&jours=...} — lecture seule, ouverte a tout
 * role authentifie.
 *
 * <p>Les previsions arrivent dans la MEME reponse que les conditions courantes,
 * plutot que derriere un second chemin : elles proviennent d'un seul appel au
 * fournisseur, et l'ecran qui les affiche affiche aussi l'instantane. Un
 * {@code jours=0} rend le comportement d'avant les previsions.
 */
@RestController
@RequestMapping("/api/meteo")
public class MeteoController {

    /** Horizon par defaut : une semaine, la portee utile d'une visite planifiee. */
    private static final int JOURS_DEFAUT = 7;

    private final MeteoService service;

    public MeteoController(MeteoService service) {
        this.service = service;
    }

    @GetMapping
    public MeteoReponse pourSite(@RequestParam Long siteId,
            @RequestParam(required = false, defaultValue = "" + JOURS_DEFAUT) int jours) {
        return service.pourSite(siteId, jours);
    }
}
