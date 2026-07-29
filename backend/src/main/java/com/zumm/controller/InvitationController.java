package com.zumm.controller;

import com.zumm.service.InvitationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Codes d'invitation d'une exploitation (US-058, ADR-009).
 *
 * <p>Reserve au responsable et a l'administrateur par la matrice RBAC : emettre
 * un code, c'est decider qui entre dans le cheptel et avec quels droits. Un
 * apiculteur qui pourrait en emettre contournerait toute la hierarchie.
 *
 * <p>Aucun identifiant d'exploitation ne transite : le tenant vient du jeton, et
 * la RLS garantit qu'un responsable ne voit ni ne revoque les codes d'une autre.
 */
@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService service;

    public InvitationController(InvitationService service) {
        this.service = service;
    }

    @GetMapping
    public List<InvitationService.InvitationReponse> lister() {
        return service.lister();
    }

    /**
     * Emet un code.
     *
     * <p>L'auteur est pris sur l'identite authentifiee, jamais dans le corps :
     * un champ « cree_par » renseigne par le client serait declaratif, donc faux
     * des qu'il compte.
     */
    @PostMapping
    public ResponseEntity<InvitationService.InvitationReponse> emettre(
            @Valid @RequestBody InvitationService.InvitationCorps corps,
            Authentication authentification) {
        var reponse = service.emettre(corps,
                authentification == null ? null : authentification.getName());
        return ResponseEntity.created(URI.create("/api/invitations/" + reponse.id())).body(reponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoquer(@PathVariable Long id) {
        service.revoquer(id);
        return ResponseEntity.noContent().build();
    }
}
