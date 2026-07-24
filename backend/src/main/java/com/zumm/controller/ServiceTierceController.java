package com.zumm.controller;

import com.zumm.service.QuantiteMielService;
import com.zumm.web.dto.QuantiteMiel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service web REST destine aux applications tierces (US-026, cahier §6.5). Le nom
 * de l'operation {@code getZummHoneyActualQuantity} est un identifiant d'API : il
 * ne se traduit pas. Le contrat est publie en OpenAPI 3 (/v3/api-docs).
 */
@RestController
@RequestMapping("/api/services")
@Tag(name = "Service tierce", description = "API exposée aux applications externes (US-026)")
public class ServiceTierceController {

    private final QuantiteMielService service;

    public ServiceTierceController(QuantiteMielService service) {
        this.service = service;
    }

    @Operation(
            summary = "getZummHoneyActualQuantity",
            description = "Quantité de miel actuelle d'une ruche (ou du rucher entier), "
                    + "dans l'unité demandée.")
    @GetMapping("/getZummHoneyActualQuantity")
    public QuantiteMiel getZummHoneyActualQuantity(
            @Parameter(description = "Ruche ciblée ; omis = total du rucher")
            @RequestParam(required = false) Long rucheId,
            @Parameter(description = "Unité de masse (kg par défaut) : mg, g, kg, t, lb")
            @RequestParam(required = false, defaultValue = "kg") String unite) {
        return service.getZummHoneyActualQuantity(rucheId, unite);
    }
}
