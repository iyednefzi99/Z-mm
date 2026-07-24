package com.zumm.controller;

import com.zumm.service.ExportService;
import com.zumm.service.ExportService.Format;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export CSV/TXT des donnees de l'utilisateur (US-027). {@code ?format=csv} (defaut)
 * ou {@code ?format=txt}. La reponse porte un {@code Content-Disposition} de
 * telechargement et le type MIME adapte.
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @GetMapping("/visites")
    public ResponseEntity<String> visites(@RequestParam(defaultValue = "csv") String format) {
        Format f = Format.depuis(format);
        return reponse("visites", f, service.exporterVisites(f));
    }

    @GetMapping("/ruches")
    public ResponseEntity<String> ruches(@RequestParam(defaultValue = "csv") String format) {
        Format f = Format.depuis(format);
        return reponse("ruches", f, service.exporterRuches(f));
    }

    private ResponseEntity<String> reponse(String base, Format format, String contenu) {
        String extension = format == Format.TXT ? "txt" : "csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"zumm-" + base + "." + extension + "\"")
                .contentType(MediaType.parseMediaType(format.typeMime() + ";charset=UTF-8"))
                .body(contenu);
    }
}
