package com.zumm.controller;

import com.zumm.service.PartageTelemetrieService;
import com.zumm.web.dto.PartageCorps;
import com.zumm.web.dto.PartageReponse;
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
 * Partages de telemetrie (SPRINT-26, lot F1).
 *
 * <p>GESTION seulement : ouvrir, lister, revoquer. La LECTURE du flux vit sous
 * {@code /api/flux/{jeton}}, dans {@link FluxPartageController} — prefixe
 * distinct, exactement comme {@code /api/calendrier/} face a
 * {@code /api/abonnements-calendrier}.
 *
 * <p>Ce n'est pas une preference d'URL : {@code TenantFilter} exempte ses
 * chemins publics par PREFIXE. Servir le flux sous {@code /api/partages/{jeton}}
 * aurait exempte du meme coup {@code DELETE /api/partages/{id}}, et la
 * revocation serait partie sans tenant.
 */
@RestController
@RequestMapping("/api/partages")
public class PartageTelemetrieController {

    private final PartageTelemetrieService service;

    public PartageTelemetrieController(PartageTelemetrieService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PartageReponse> creer(@Valid @RequestBody PartageCorps corps,
            HttpServletRequest requete) {
        PartageReponse reponse = service.creer(corps, base(requete));
        return ResponseEntity.created(URI.create("/api/partages/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<PartageReponse> lister(@RequestParam Long rucheId) {
        return service.lister(rucheId);
    }

    /** Revoque : la ligne demeure, l'URL cesse de repondre. */
    @DeleteMapping("/{id}")
    public PartageReponse revoquer(@PathVariable Long id) {
        return service.revoquer(id);
    }

    /** Origine de la requete, pour composer l'URL rendue une seule fois. */
    private static String base(HttpServletRequest requete) {
        String origine = requete.getScheme() + "://" + requete.getServerName();
        int port = requete.getServerPort();
        boolean portParDefaut = (port == 80 && "http".equals(requete.getScheme()))
                || (port == 443 && "https".equals(requete.getScheme()));
        return portParDefaut ? origine : origine + ":" + port;
    }
}
