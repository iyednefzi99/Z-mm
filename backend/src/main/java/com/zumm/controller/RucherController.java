package com.zumm.controller;

import com.zumm.service.EmportRucherService;
import com.zumm.service.CarnetService;
import com.zumm.service.FicheInspectionPdfService;
import com.zumm.service.SyntheseRucherService;
import com.zumm.web.dto.EmportRucher;
import com.zumm.web.dto.SyntheseRucher;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le rucher comme niveau de lecture (SPRINT-23, lot B).
 *
 * <p>Ressource distincte de {@code /api/sites}, et c'est voulu : celle-la rend le
 * REFERENTIEL — nom, position, adresse, ressources —, celle-ci rend un ETAT
 * agrege qui ne se stocke nulle part. Les melanger aurait alourdi chaque lecture
 * de site d'un calcul que la carte n'utilise pas.
 */
@RestController
@RequestMapping("/api/ruchers")
public class RucherController {

    private final SyntheseRucherService service;
    private final EmportRucherService emports;
    private final FicheInspectionPdfService fiches;
    private final CarnetService carnet;

    public RucherController(SyntheseRucherService service, EmportRucherService emports,
            FicheInspectionPdfService fiches, CarnetService carnet) {
        this.service = service;
        this.emports = emports;
        this.fiches = fiches;
        this.carnet = carnet;
    }

    /** Sans {@code siteId}, tous les ruchers, du plus preoccupant au plus calme. */
    @GetMapping("/synthese")
    public List<SyntheseRucher> synthese(@RequestParam(required = false) Long siteId) {
        return siteId == null ? service.tous() : List.of(service.pourSite(siteId));
    }

    /**
     * Instantane complet d'un rucher, a emporter hors ligne (SPRINT-24, lot C).
     *
     * <p>Un seul appel, horodate par le serveur : sur un reseau qui s'effondre —
     * le contexte meme de la fonction — un emport a moitie fait serait pire que
     * pas d'emport, parce qu'il aurait l'air complet.
     *
     * <p>Ce n'est PAS un cache : c'est une ressource demandee, bornee, datee et
     * purgeable ({@code ADR-012}). Le refus de cacher {@code /api} inscrit dans
     * {@code vite.config.ts} reste entier.
     */
    @GetMapping("/{siteId}/emport")
    public EmportRucher emporter(@PathVariable Long siteId) {
        return emports.pourSite(siteId);
    }

    /**
     * Fiche d'inspection VIERGE du rucher, une ligne par ruche (SPRINT-24).
     *
     * <p>L'inverse du rapport de visite : celui-la rend compte apres coup,
     * celle-ci est le support de saisie qu'on emporte. C'est la reponse la moins
     * chere au probleme des gants, et trois editeurs concurrents conseillent
     * deja cette manoeuvre a leurs utilisateurs.
     */
    @GetMapping(value = "/{siteId}/fiche-inspection.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> ficheInspection(@PathVariable Long siteId) {
        EmportRucher rucher = emports.pourSite(siteId);
        // La fiche suit le gabarit par defaut (SPRINT-28) : le papier demande ce
        // que l'ecran demande, faute de quoi la ressaisie exige une traduction.
        byte[] pdf = fiches.generer(rucher.site().nom(), rucher.ruches(),
                carnet.gabaritParDefaut(), carnet.points());
        // `inline` et non `attachment` : la fiche se relit a l'ecran avant d'etre
        // imprimee, et forcer un telechargement ajouterait un geste a chaque fois.
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"fiche-inspection-%d.pdf\"".formatted(siteId))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
