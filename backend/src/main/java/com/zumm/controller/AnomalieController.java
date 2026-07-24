package com.zumm.controller;

import com.zumm.domain.TypeIndicateur;
import com.zumm.service.AnomalieService;
import com.zumm.web.dto.AnomalieReponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Detection d'anomalie adaptative EWMA (US-034).
 * {@code GET /api/anomalies?rucheId=...&type=poids}.
 */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalieController {

    private final AnomalieService service;

    public AnomalieController(AnomalieService service) {
        this.service = service;
    }

    @GetMapping
    public AnomalieReponse detecter(
            @RequestParam Long rucheId,
            @RequestParam TypeIndicateur type) {
        return service.detecter(rucheId, type);
    }
}
