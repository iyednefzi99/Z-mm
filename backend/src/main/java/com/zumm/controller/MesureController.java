package com.zumm.controller;

import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.AlerteRepository;
import com.zumm.service.MesureService;
import com.zumm.web.dto.AlerteReponse;
import com.zumm.web.dto.MesureCorps;
import com.zumm.web.dto.MesureReponse;
import com.zumm.web.dto.PointJournalier;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestion et lecture des mesures de capteurs (US-017), avec les alertes de
 * seuils declenchees (US-018). {@code POST /api/mesures} est le canal REST ; le
 * pont MQTT appelle le meme service d'ingestion.
 */
@RestController
@RequestMapping("/api/mesures")
public class MesureController {

    private final MesureService service;
    private final AlerteRepository alertes;

    public MesureController(MesureService service, AlerteRepository alertes) {
        this.service = service;
        this.alertes = alertes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MesureReponse ingerer(@Valid @RequestBody MesureCorps corps) {
        return service.ingerer(corps);
    }

    @GetMapping
    public List<MesureReponse> serie(
            @RequestParam Long rucheId,
            @RequestParam TypeIndicateur type) {
        return service.serie(rucheId, type);
    }

    /**
     * Serie JOURNALIERE d'un indicateur (SPRINT-18) : un point par jour.
     *
     * <p>Endpoint distinct plutot qu'un parametre sur {@code GET /api/mesures} :
     * les deux ne rendent pas la meme chose — l'un des mesures, l'autre des
     * compartiments avec minimum et maximum. Les confondre derriere un drapeau
     * obligerait tout appelant a savoir lequel il recoit.
     */
    @GetMapping("/journalier")
    public List<PointJournalier> serieJournaliere(
            @RequestParam Long rucheId,
            @RequestParam TypeIndicateur type) {
        return service.serieJournaliere(rucheId, type);
    }

    /** Alertes de seuils actuellement ouvertes (US-018). */
    @GetMapping("/alertes")
    public List<AlerteReponse> alertesOuvertes() {
        return alertes.findByOuverteTrueOrderByOuverteLeDesc().stream().map(AlerteReponse::de).toList();
    }
}
