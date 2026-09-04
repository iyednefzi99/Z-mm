package com.zumm.controller;

import com.zumm.service.AbonnementCalendrierService;
import com.zumm.web.dto.AbonnementCorps;
import com.zumm.web.dto.AbonnementReponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion des abonnements iCalendar (SPRINT-21).
 *
 * <p>Contrairement au FLUX, qui est public par necessite, la gestion reste
 * derriere la session : emettre et revoquer un jeton sont des actes de securite.
 *
 * <p>{@code POST} rend l'URL complete <strong>une seule fois</strong>. Elle
 * n'est reconstructible ni par cette API ni par la base, qui n'en garde que
 * l'empreinte : perdue, elle se remplace, elle ne se retrouve pas.
 */
@RestController
@RequestMapping("/api/abonnements-calendrier")
public class AbonnementCalendrierController {

    private final AbonnementCalendrierService service;

    public AbonnementCalendrierController(AbonnementCalendrierService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AbonnementReponse> creer(@Valid @RequestBody AbonnementCorps corps,
            HttpServletRequest requete) {
        AbonnementReponse reponse = service.creer(corps, base(requete));
        return ResponseEntity
                .created(URI.create("/api/abonnements-calendrier/" + reponse.id()))
                .body(reponse);
    }

    @GetMapping
    public List<AbonnementReponse> lister(@RequestParam Long agentId) {
        return service.lister(agentId);
    }

    /** Revoque : la ligne demeure, l'URL cesse de repondre. */
    @DeleteMapping("/{id}")
    public AbonnementReponse revoquer(@PathVariable Long id) {
        return service.revoquer(id);
    }

    /**
     * Origine a placer devant le chemin du flux.
     *
     * <p>Reconstruite depuis la requete plutot que configuree : derriere le proxy
     * inverse, l'application ne connait pas son URL publique, et une origine
     * codee en dur donnerait une URL d'abonnement qui ne repond pas.
     */
    private static String base(HttpServletRequest requete) {
        String origine = requete.getScheme() + "://" + requete.getServerName();
        int port = requete.getServerPort();
        boolean portParDefaut = (port == 80 && "http".equals(requete.getScheme()))
                || (port == 443 && "https".equals(requete.getScheme()));
        return portParDefaut ? origine : origine + ":" + port;
    }
}
