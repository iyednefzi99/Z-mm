package com.zumm.controller;

import com.zumm.service.MesureCompartimentService;
import com.zumm.web.dto.MesureCompartimentCorps;
import com.zumm.web.dto.PoidsCompartiment;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Poids par compartiment (SPRINT-26, lot F1).
 *
 * <p>Ressource DISTINCTE de {@code /api/mesures}, et c'est voulu : celle-la
 * porte ce qu'une balance pese sous la ruche entiere, celle-ci ce qu'on attribue
 * a une hausse. Les servir par la meme route aurait demande a chaque appelant de
 * savoir laquelle des deux il interroge — et a chaque lecture existante de se
 * souvenir d'exclure l'autre.
 */
@RestController
@RequestMapping("/api/compartiments")
public class MesureCompartimentController {

    private final MesureCompartimentService service;

    public MesureCompartimentController(MesureCompartimentService service) {
        this.service = service;
    }

    /** Enregistre une pesee. 200 et non 201 : une mesure n'a pas d'URL propre. */
    @PostMapping("/mesures")
    public PoidsCompartiment peser(@Valid @RequestBody MesureCompartimentCorps corps) {
        return service.peser(corps);
    }

    /**
     * Repartition du poids d'une ruche, un compartiment par ligne.
     *
     * <p>Les compartiments jamais peses figurent avec un poids nul : les taire
     * donnerait une repartition qui a l'air complete et ne l'est pas.
     */
    @GetMapping("/repartition")
    public List<PoidsCompartiment> repartition(@RequestParam Long rucheId) {
        return service.repartition(rucheId);
    }

    /** Serie d'un compartiment sur une fenetre. */
    @GetMapping("/{id}/serie")
    public List<PoidsCompartiment> serie(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant debut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fin) {
        return service.serie(id, debut, fin);
    }
}
