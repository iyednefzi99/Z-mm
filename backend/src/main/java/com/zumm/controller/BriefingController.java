package com.zumm.controller;

import com.zumm.service.BriefingService;
import com.zumm.web.dto.Briefing;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Briefing du jour (SPRINT-30, lot G).
 *
 * <p>Une seule route, en lecture. Rien n'est stocke : le briefing d'hier n'a
 * aucun interet, et le recalculer coute moins cher que de le conserver.
 *
 * <p>Ouverte a tout role metier, deliberement : c'est l'ecran qu'un apiculteur
 * regarde le matin avant de partir, et le reserver au pilotage en ferait un
 * tableau de bord de plus.
 */
@RestController
@RequestMapping("/api/briefing")
public class BriefingController {

    private final BriefingService service;

    public BriefingController(BriefingService service) {
        this.service = service;
    }

    /**
     * Ce qui merite l'attention aujourd'hui.
     *
     * <p>{@code jour} sert aux tests et a la relecture d'une journee passee ;
     * absent, c'est aujourd'hui.
     */
    @GetMapping
    public Briefing duJour(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jour) {
        return service.duJour(jour == null ? LocalDate.now() : jour);
    }
}
