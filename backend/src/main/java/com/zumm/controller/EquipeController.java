package com.zumm.controller;

import com.zumm.service.ChargeEquipeService;
import com.zumm.web.dto.ChargeAgent;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Coordination d'equipe (SPRINT-23, lot B).
 *
 * <p>En lecture seule : la charge se constate, elle ne se saisit pas. Une route
 * d'ecriture laisserait croire qu'on « affecte » une charge, alors qu'on affecte
 * des ruches et des taches — chacune par sa propre ressource.
 */
@RestController
@RequestMapping("/api/equipe")
public class EquipeController {

    private final ChargeEquipeService service;

    public EquipeController(ChargeEquipeService service) {
        this.service = service;
    }

    @GetMapping("/charge")
    public List<ChargeAgent> charge() {
        return service.charge();
    }
}
