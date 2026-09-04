package com.zumm.controller;

import com.zumm.service.ExportService;
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
 * Export tabulaire (US-027, étendu au SPRINT-27, lot E).
 *
 * <p>Au SPRINT-09, deux routes figées — {@code /visites} et {@code /ruches}. Le
 * §7 relevait que « douze catalogues promettent tous l'export intégral » ; la
 * ressource est désormais un <strong>paramètre de chemin</strong>, et le service
 * en connaît quatorze.
 *
 * <p>Les deux anciennes routes sont conservées telles quelles : elles sont
 * utilisées par l'interface depuis le SPRINT-09, et les casser pour gagner deux
 * méthodes aurait été un mauvais échange.
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    /** Les ressources exportables, pour que l'écran n'ait pas à les deviner. */
    @GetMapping("/ressources")
    public List<String> ressources() {
        return ExportService.ressources();
    }

    @GetMapping("/visites")
    public ResponseEntity<byte[]> visites(@RequestParam(defaultValue = "csv") String format) {
        return fichier("visites", format);
    }

    @GetMapping("/ruches")
    public ResponseEntity<byte[]> ruches(@RequestParam(defaultValue = "csv") String format) {
        return fichier("ruches", format);
    }

    /**
     * N'importe quelle ressource exportable, dans l'un des trois formats.
     *
     * <p>Exemple : {@code GET /api/export/depenses?format=xlsx}. Une ressource
     * inconnue rend 400 avec son nom — un 404 laisserait croire à une faute
     * d'URL alors que c'est le nom de la ressource qui est faux.
     */
    @GetMapping("/{ressource}")
    public ResponseEntity<byte[]> exporter(@PathVariable String ressource,
            @RequestParam(defaultValue = "csv") String format) {
        return fichier(ressource, format);
    }

    private ResponseEntity<byte[]> fichier(String ressource, String format) {
        ExportService.Format demande = ExportService.Format.depuis(format);
        byte[] contenu = service.exporter(ressource, demande);
        return ResponseEntity.ok()
                // `attachment` : un export se garde, il ne se lit pas dans
                // l'onglet — contrairement à la fiche d'inspection du SPRINT-24.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"zumm-%s.%s\"".formatted(
                                ressource, demande.extension()))
                .contentType(MediaType.parseMediaType(demande.typeMime()))
                .body(contenu);
    }
}
