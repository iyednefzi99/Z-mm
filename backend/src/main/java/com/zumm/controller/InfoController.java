package com.zumm.controller;

import com.zumm.config.PolitiqueReseau;
import com.zumm.config.ZummProperties;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expose l'identite de l'application et verifie de bout en bout la chaine
 * configuration + internationalisation.
 *
 * <p>C'est l'endpoint trivial du walking skeleton : il ne porte aucune regle
 * metier et n'a pas vocation a survivre au Sprint 1 sous cette forme.
 */
@RestController
@RequestMapping("/api")
public class InfoController {

    private final ZummProperties proprietes;
    private final MessageSource messages;
    private final PolitiqueReseau reseau;

    public InfoController(ZummProperties proprietes, MessageSource messages,
            PolitiqueReseau reseau) {
        this.proprietes = proprietes;
        this.messages = messages;
        this.reseau = reseau;
    }

    /**
     * Renvoie l'identite de l'application, le message d'accueil traduit dans la
     * locale demandee et la liste des langues actives.
     */
    @GetMapping("/info")
    public Info info(Locale locale) {
        return new Info(
                proprietes.nom(),
                proprietes.version(),
                messages.getMessage("app.accueil", null, locale),
                proprietes.languesActives(),
                proprietes.auth().reinitialisationUrl(),
                reseau.sortantAutorise());
    }

    /** Reponse de l'endpoint d'identite. */
    public record Info(String nom, String version, String accueil, List<String> langues,
            /**
             * Parcours « mot de passe oublie » du fournisseur d'identite, ou
             * chaine VIDE s'il n'est pas configure (SPRINT-25).
             *
             * <p>Rendue par une route PUBLIQUE, et il le faut : celui qui a
             * oublie son mot de passe n'est, par construction, pas connecte.
             * Ce n'est pas un secret — c'est l'adresse d'une page de connexion.
             */
            String reinitialisationUrl,
            /**
             * Le serveur s'autorise-t-il des appels sortants (SPRINT-30) ?
             *
             * <p>Rendue par la meme route publique que le reste : ce n'est pas
             * un secret, c'est une PROMESSE que l'interface doit pouvoir
             * afficher. Un exploitant qui a coupe le reseau sortant veut le voir
             * ecrit, et celui qui ne l'a pas coupe ne doit pas croire l'avoir
             * fait.
             */
            boolean reseauSortant) {
    }
}
