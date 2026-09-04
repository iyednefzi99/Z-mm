package com.zumm.controller;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.service.CalculateurApicole;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calculateurs apicoles (SPRINT-27, lot E).
 *
 * <p>Fonctions pures derriere des routes de lecture : rien n'est ecrit, rien
 * n'est lu en base. Le patron est celui de {@code ConversionController}, livre
 * au SPRINT-01.
 *
 * <p>Le refractometre n'y figure pas, et c'est deliberé : la conversion d'un
 * indice de refraction en taux d'humidite demande une table propre a chaque
 * appareil. L'implementer au jugé donnerait un chiffre faux sur une mesure qui
 * decide de la conservation du miel.
 */
@RestController
@RequestMapping("/api/calculateurs")
public class CalculateurController {

    private final ConfigurationMetier configuration;

    public CalculateurController(ConfigurationMetier configuration) {
        this.configuration = configuration;
    }

    /**
     * Sucre et eau pour un volume de sirop.
     *
     * <p>Exemple : {@code GET /api/calculateurs/sirop?proportion=2:1&litres=10}.
     */
    @GetMapping("/sirop")
    public CalculateurApicole.Sirop sirop(
            @RequestParam(defaultValue = "1:1") String proportion,
            @RequestParam BigDecimal litres) {
        return CalculateurApicole.sirop(proportion, litres);
    }

    /**
     * Valorisation d'une production de miel.
     *
     * <p>Le prix par defaut est celui de {@code ConfigZumm.ini} : c'est
     * l'hypothese que l'exploitation a deja posee, et la reprendre evite deux
     * chiffres contradictoires dans le meme produit.
     */
    @GetMapping("/valorisation")
    public CalculateurApicole.Valorisation valorisation(
            @RequestParam BigDecimal kilos,
            @RequestParam(required = false) BigDecimal prixKgEur) {
        BigDecimal prix = prixKgEur == null ? configuration.seuils().prixMielKgEur() : prixKgEur;
        return CalculateurApicole.valoriser(kilos, prix);
    }
}
