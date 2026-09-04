package com.zumm.controller;

import com.zumm.service.BilanAnnuelPdfService;
import com.zumm.service.ComparaisonSaisonsService;
import com.zumm.service.ComptabiliteService;
import com.zumm.web.dto.ComparaisonSaisons;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saison contre saison (SPRINT-27, lot E).
 *
 * <p>Ressource distincte des tableaux de bord, qui raisonnent tous en PERIODE
 * GLISSANTE : douze mois qui reculent chaque jour ne permettent pas de dire
 * « 2026 a mieux donne que 2025 ».
 */
@RestController
@RequestMapping("/api/saisons")
public class SaisonController {

    private final ComparaisonSaisonsService service;
    private final ComptabiliteService comptabilite;
    private final BilanAnnuelPdfService pdf;

    public SaisonController(ComparaisonSaisonsService service, ComptabiliteService comptabilite,
            BilanAnnuelPdfService pdf) {
        this.service = service;
        this.comptabilite = comptabilite;
        this.pdf = pdf;
    }

    /** Les saisons enregistrees, de la plus recente a la plus ancienne. */
    @GetMapping
    public List<ComparaisonSaisons> saisons() {
        return service.saisons();
    }

    /**
     * Bilan annuel en PDF : un document qu'on ARCHIVE, pas un tableau de bord.
     *
     * <p>Il porte une annee civile close, ses chiffres ne bougent plus, et il se
     * range — avec la declaration de ruchers, chez le comptable, ou dans un
     * dossier de controle.
     */
    @GetMapping(value = "/{annee}/bilan.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> bilanAnnuel(@PathVariable int annee) {
        LocalDate debut = LocalDate.of(annee, Month.JANUARY, 1);
        LocalDate fin = LocalDate.of(annee, Month.DECEMBER, 31);
        ComparaisonSaisons saison = service.saisons().stream()
                .filter(s -> s.annee() == annee)
                .findFirst()
                // Nulle quand l'annee n'a rien produit : le PDF sort quand meme,
                // avec ses depenses. Une exploitation qui a depense sans recolter
                // a precisement besoin de ce document-la.
                .orElse(null);
        byte[] document = pdf.generer(annee, comptabilite.bilan(debut, fin), saison);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"zumm-bilan-%d.pdf\"".formatted(annee))
                .contentType(MediaType.APPLICATION_PDF)
                .body(document);
    }
}
