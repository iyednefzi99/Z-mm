package com.zumm.controller;

import com.zumm.service.ConversionUnites;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conversion d'unites heterogenes (US-019). Exemple :
 * {@code GET /api/conversions?valeur=1500&de=g&vers=kg} -> 1.5.
 */
@RestController
@RequestMapping("/api/conversions")
public class ConversionController {

    private final ConversionUnites conversion;

    public ConversionController(ConversionUnites conversion) {
        this.conversion = conversion;
    }

    @GetMapping
    public Resultat convertir(
            @RequestParam double valeur,
            @RequestParam String de,
            @RequestParam String vers) {
        return new Resultat(valeur, de, vers, conversion.convertir(valeur, de, vers));
    }

    /** Reponse de conversion. */
    public record Resultat(double valeur, String de, String vers, double resultat) {
    }
}
