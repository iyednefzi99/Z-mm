package com.zumm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zumm.service.CouvertSolService;
import com.zumm.service.FloraisonService;
import com.zumm.web.dto.CouvertRucher;
import com.zumm.web.dto.FloraisonCorps;
import com.zumm.web.dto.FloraisonReponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
 * Environnement d'un rucher : couvert du sol et floraison (SPRINT-32, lot H).
 *
 * <p>Le dernier lot du plan de couverture, et le seul terrain ou un concurrent —
 * BeeGIS — joue sur le domaine que Zumm revendique.
 *
 * <p><strong>Aucune route ne va chercher la donnee dehors</strong>
 * ([ADR-015]) : l'exploitation VERSE sa couche, et tout le calcul se fait chez
 * elle. Interroger un service tiers avec les coordonnees d'un rucher lui
 * apprendrait ou sont les ruches — ce que `PolitiquePositions` protege depuis le
 * SPRINT-12 —, et serait muet en mode local depuis le SPRINT-30.
 */
@RestController
@RequestMapping("/api/environnement")
public class EnvironnementController {

    private final CouvertSolService couvert;
    private final FloraisonService floraisons;

    public EnvironnementController(CouvertSolService couvert, FloraisonService floraisons) {
        this.couvert = couvert;
        this.floraisons = floraisons;
    }

    // ─── Couvert du sol ──────────────────────────────────────────────────────

    /**
     * Verse une couche d'occupation du sol.
     *
     * <p>Une {@code FeatureCollection} GeoJSON dont chaque entite porte une
     * propriete {@code classe} de la taxonomie fermee. Le millesime est
     * obligatoire, et remplace celui qui existait : verser deux fois la meme
     * annee sans purger doublerait toutes les surfaces.
     */
    @PostMapping("/couvert")
    public Map<String, Object> verser(
            @RequestParam String source,
            @RequestParam int millesime,
            @RequestBody JsonNode collection) {
        return Map.of("polygones", couvert.verser(source, millesime, collection),
                "millesime", millesime, "source", source);
    }

    /** Millesimes verses, du plus recent au plus ancien. */
    @GetMapping("/couvert/millesimes")
    public List<Integer> millesimes() {
        return couvert.millesimes();
    }

    /**
     * Retire un millesime entier.
     *
     * <p>Une couche se remplace, elle ne se corrige pas ligne a ligne : purger
     * puis reverser est le seul geste qui garantisse un total coherent.
     */
    @DeleteMapping("/couvert")
    public Map<String, Integer> purger(@RequestParam int millesime) {
        return Map.of("supprimes", couvert.purger(millesime));
    }

    /**
     * Ce qu'il y a autour d'un rucher.
     *
     * <p>Sans {@code millesime}, le plus recent. Les surfaces sont geodesiques
     * et bornees au rayon de butinage du rucher.
     */
    @GetMapping("/sites/{siteId}/couvert")
    public CouvertRucher autour(@PathVariable Long siteId,
            @RequestParam(required = false) Integer millesime) {
        return couvert.autour(siteId, millesime);
    }

    /**
     * Les memes surfaces, millesime par millesime.
     *
     * <p>La rotation des cultures ne se DEDUIT pas d'une couche : elle se LIT en
     * comparant deux annees. C'est pour cela que le millesime est obligatoire
     * des la premiere ligne versee.
     */
    @GetMapping("/sites/{siteId}/rotation")
    public List<CouvertRucher> rotation(@PathVariable Long siteId) {
        return couvert.rotation(siteId);
    }

    // ─── Floraison observee ──────────────────────────────────────────────────

    /** Floraisons observees, toutes ou celles d'un rucher. */
    @GetMapping("/floraisons")
    public List<FloraisonReponse> floraisons(@RequestParam(required = false) Long siteId) {
        return floraisons.lister(siteId);
    }

    /**
     * Enregistre ou complete l'observation de l'annee.
     *
     * <p>Une ressource ne fleurit qu'une fois par an : une seconde saisie
     * COMPLETE la premiere au lieu d'echouer. L'apiculteur note le debut en
     * avril et le pic en mai.
     */
    @PostMapping("/floraisons")
    public ResponseEntity<FloraisonReponse> enregistrer(
            @Valid @RequestBody FloraisonCorps corps) {
        FloraisonReponse reponse = floraisons.enregistrer(corps);
        return ResponseEntity
                .created(URI.create("/api/environnement/floraisons/" + reponse.id()))
                .body(reponse);
    }

    @DeleteMapping("/floraisons/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        floraisons.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
