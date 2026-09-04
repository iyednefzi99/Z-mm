package com.zumm.controller;

import com.zumm.service.JeuDemonstrationService;
import com.zumm.web.dto.EtatDemonstration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Jeu de demonstration chargeable et retirable (SPRINT-25, lot J).
 *
 * <p>Les trois verbes disent exactement ce qu'ils font : lire l'etat, charger,
 * retirer. Pas de « reinitialiser » qui melangerait les deux — un utilisateur
 * qui veut repartir de zero doit voir passer la suppression.
 *
 * <p>L'acces est reserve au role {@code admin} par {@code SecurityConfig} :
 * ecrire vingt lignes dans l'exploitation d'autrui n'est pas un geste
 * d'apiculteur. Et la fonction est ETEINTE par defaut sur un deploiement, ce que
 * {@code GET} rend explicite plutot que de laisser le bouton echouer.
 */
@RestController
@RequestMapping("/api/demonstration")
public class DemonstrationController {

    private final JeuDemonstrationService service;

    public DemonstrationController(JeuDemonstrationService service) {
        this.service = service;
    }

    @GetMapping
    public EtatDemonstration etat() {
        return service.etat();
    }

    @PostMapping
    public EtatDemonstration charger() {
        return service.charger();
    }

    @DeleteMapping
    public EtatDemonstration purger() {
        return service.purger();
    }
}
