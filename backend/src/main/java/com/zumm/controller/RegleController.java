package com.zumm.controller;

import com.zumm.service.regles.MoteurRegles;
import com.zumm.web.dto.TacheReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Execution du moteur de regles (SPRINT-22, lot A).
 *
 * <p>Une seule route, et elle est {@code POST} parce qu'elle CREE des taches.
 *
 * <p><strong>Declenchee a la main, et c'est un choix.</strong> Le moteur est
 * idempotent — le passer deux fois dans la journee ne produit rien la seconde
 * fois —, donc rien n'interdirait de le brancher sur un planificateur. Mais une
 * exploitation qui decouvre un matin quinze taches qu'elle n'a pas demandees
 * cesse de lire sa liste. On commence par la commande explicite ; l'automatiser
 * viendra quand les regles auront fait leurs preuves sur du reel.
 */
@RestController
@RequestMapping("/api/regles")
public class RegleController {

    private final MoteurRegles moteur;

    public RegleController(MoteurRegles moteur) {
        this.moteur = moteur;
    }

    /** Rend les taches CREEES — vide si tout existait deja, et c'est normal. */
    @PostMapping("/executer")
    public List<TacheReponse> executer() {
        return moteur.executer(LocalDate.now()).stream().map(TacheReponse::de).toList();
    }
}
