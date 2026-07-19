package com.zumm.controller;

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

    public InfoController(ZummProperties proprietes, MessageSource messages) {
        this.proprietes = proprietes;
        this.messages = messages;
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
                proprietes.languesActives());
    }

    /** Reponse de l'endpoint d'identite. */
    public record Info(String nom, String version, String accueil, List<String> langues) {
    }
}
