package com.zumm.controller;

import com.zumm.service.FluxCalendrierService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Flux iCalendar d'abonnement (SPRINT-21).
 *
 * <p><strong>La seule route metier du depot servie sans authentification</strong>,
 * et elle l'est par necessite : un client de calendrier — Google Agenda, Outlook,
 * Apple Calendar — appelle une URL toutes les quelques heures, sans session ni
 * en-tete. Le jeton du chemin tient donc lieu d'authentification.
 *
 * <p>Ce que cela coute, et comment c'est borne : le jeton fait 256 bits, n'est
 * stocke qu'en empreinte, expire, se revoque, et ne donne acces qu'a l'agenda
 * d'UN agent, sans aucune position (voir {@code AbonnementCalendrierService} et
 * {@code AgendaIcsService}).
 *
 * <p>Un jeton inconnu, revoque ou expire recoit le meme <strong>404</strong> :
 * distinguer les trois confirmerait a un appelant qu'un jeton a existe.
 */
@RestController
public class FluxCalendrierController {

    private final FluxCalendrierService service;

    public FluxCalendrierController(FluxCalendrierService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/calendrier/{jeton}.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> flux(@PathVariable String jeton) {
        return service.calendrier(jeton)
                .map(ics -> ResponseEntity.ok()
                        // Un abonnement se relit souvent : on demande aux
                        // intermediaires de ne rien garder, faute de quoi une
                        // visite annulee resterait affichee des heures.
                        .header("Cache-Control", "no-store")
                        .body(ics))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
