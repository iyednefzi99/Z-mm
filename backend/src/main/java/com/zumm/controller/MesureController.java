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

    /**
     * Plafond d'un lot d'ingestion.
     *
     * <p>Cinq cents mesures couvrent un rucher entier sur plusieurs releves ;
     * au-dela, la requete devient un import, et un import se fait autrement.
     * Sans plafond, une passerelle en boucle peut immobiliser une transaction
     * sur l'hypertable la plus ecrite du systeme.
     */
    private static final int TAILLE_LOT_MAX = 500;

    private final MesureService service;
    private final AlerteRepository alertes;

    public MesureController(MesureService service, AlerteRepository alertes) {
        this.service = service;
        this.alertes = alertes;
    }

    /**
     * Ingestion par lot, pour une passerelle (SPRINT-31, lot F2).
     *
     * <p>Le meme traitement que l'unitaire, applique en une transaction : tout
     * passe ou rien ne passe. C'est ce qui permet a une passerelle de rejouer
     * un lot entier apres une coupure sans avoir a deviner ce qui est arrive.
     */
    @PostMapping("/lot")
    public List<MesureReponse> ingererLot(
            @Valid @RequestBody @jakarta.validation.constraints.NotEmpty
            @jakarta.validation.constraints.Size(max = TAILLE_LOT_MAX)
            List<@jakarta.validation.Valid MesureCorps> corps) {
        return service.ingererLot(corps);
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
        // Delegue au service : la lecture doit etre TRANSACTIONNELLE, `Alerte.ruche`
        // etant LAZY. Faite ici, elle levait une `LazyInitializationException` des
        // qu'une alerte existait — voir `MesureService.alertesOuvertes`.
        return service.alertesOuvertes();
    }
}
