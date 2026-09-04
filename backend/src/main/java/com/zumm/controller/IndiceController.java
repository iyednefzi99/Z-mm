package com.zumm.controller;

import com.zumm.service.IndiceColonieService;
import com.zumm.web.dto.IndiceColonie;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indices de colonie : sante et risque d'essaimage (SPRINT-22, lot A).
 *
 * <p>En lecture seule, et il n'y a rien a ecrire : ces indices se calculent, ils
 * ne se saisissent pas. Une route {@code POST} laisserait croire qu'on peut
 * corriger une note — ce serait corriger le thermometre.
 */
@RestController
@RequestMapping("/api/indices")
public class IndiceController {

    private final IndiceColonieService service;

    public IndiceController(IndiceColonieService service) {
        this.service = service;
    }

    /** Sans {@code rucheId}, tout le parc, du plus preoccupant au plus sain. */
    @GetMapping
    public List<IndiceColonie> lister(@RequestParam(required = false) Long rucheId) {
        return rucheId == null ? service.parc() : List.of(service.pourRuche(rucheId));
    }
}
