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
 * <p>Le refractometre s'y ajoute au SPRINT-28, apres rectification : la table
 * de correspondance est publiee et vaut pour tout miel ; ce qui appartient a
 * l'appareil, c'est son etalonnage. La conversion dit donc de quoi elle part, et
 * refuse tout ce qui sort de la plage tabulee.
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

    /**
     * Taux d'eau d'un miel, lu au refractometre (SPRINT-28, lot I).
     *
     * <p>Exemple : {@code GET /api/calculateurs/refractometre?indice=1.4930&temperatureC=25}.
     * La temperature est celle de la MESURE ; un appareil thermocompense se
     * declare a 20 °C, valeur par defaut.
     */
    @GetMapping("/refractometre")
    public CalculateurApicole.Refractometre refractometre(
            @RequestParam BigDecimal indice,
            @RequestParam(required = false) BigDecimal temperatureC) {
        return CalculateurApicole.humiditeMiel(indice, temperatureC);
    }
}
