package com.zumm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zumm.service.CouvertSolService;
import com.zumm.service.FloraisonService;
import com.zumm.service.ZoneTraiteeService;
import com.zumm.web.dto.ConstatCouvertCorps;
import com.zumm.web.dto.CouvertRucher;
import com.zumm.web.dto.ExpositionRucher;
import com.zumm.web.dto.FiabiliteCouvert;
import com.zumm.web.dto.FloraisonCorps;
import com.zumm.web.dto.FloraisonReponse;
import com.zumm.web.dto.ParcelleCouvert;
import com.zumm.web.dto.ZoneTraiteeCorps;
import com.zumm.web.dto.ZoneTraiteeReponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
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
 * Environnement d'un rucher : couvert du sol, floraison, verification terrain et
 * zones traitees declarees (SPRINT-32 lot H, SPRINT-33 lot K).
 *
 * <p>Le terrain ou un concurrent — BeeGIS — joue sur le domaine que Zumm
 * revendique. Le SPRINT-33 y ajoute les deux gestes que BeeGIS conseille sans
 * les outiller : verifier au printemps ce qui a reellement ete seme, et savoir
 * ce qui a ete traite autour du rucher.
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
    private final ZoneTraiteeService zones;

    public EnvironnementController(CouvertSolService couvert, FloraisonService floraisons,
            ZoneTraiteeService zones) {
        this.couvert = couvert;
        this.floraisons = floraisons;
        this.zones = zones;
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

    // ─── Verification terrain (SPRINT-33, lot K) ─────────────────────────────

    /**
     * Parcelles de la couche, ou celles d'un rucher.
     *
     * <p>{@code enAttente=true} — le defaut — ne rend que ce que le terrain doit
     * trancher. Sans cette borne, la reponse porterait la couche entiere, soit
     * des milliers de polygones dont personne ne doute.
     */
    @GetMapping("/couvert/parcelles")
    public List<ParcelleCouvert> parcelles(
            @RequestParam(required = false) Long siteId,
            @RequestParam(defaultValue = "true") boolean enAttente) {
        return couvert.parcelles(siteId, enAttente);
    }

    /**
     * Pose ou leve le doute sur une parcelle.
     *
     * <p>Un geste humain, et il le reste : deduire le doute fabriquerait une
     * tournee de verification que personne n'a demandee. La regle
     * {@code verification-couvert} transforme ensuite ces marques en UNE tache
     * par rucher et par saison.
     */
    @PostMapping("/couvert/parcelles/{id}/a-confirmer")
    public ResponseEntity<Void> marquer(@PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean valeur) {
        couvert.marquer(id, valeur);
        return ResponseEntity.noContent().build();
    }

    /**
     * Enregistre ce que le terrain a montre — le <em>ground truthing</em> du §13.
     *
     * <p>La classe de la SOURCE n'est jamais ecrasee : ecrasee, la parcelle
     * raconterait que la couche avait raison depuis le debut, et plus personne ne
     * pourrait dire de quel millesime se defier. Le constat s'ecrit a cote, et
     * les surfaces le prennent des qu'il existe.
     */
    @PostMapping("/couvert/parcelles/{id}/constat")
    public ResponseEntity<Void> constater(@PathVariable Long id,
            @Valid @RequestBody ConstatCouvertCorps corps) {
        couvert.constater(id, corps);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ce que le terrain a appris sur un millesime.
     *
     * <p>Trois nombres, aucun pourcentage : un « taux d'exactitude de 100 % »
     * calcule sur deux visites serait lu comme un verdict sur la couche entiere.
     */
    @GetMapping("/couvert/fiabilite")
    public FiabiliteCouvert fiabilite(@RequestParam int millesime) {
        return couvert.fiabilite(millesime);
    }

    // ─── Zones traitees declarees (SPRINT-33, lot K) ─────────────────────────

    /** Zones traitees declarees par l'exploitation, de la plus recente a la plus ancienne. */
    @GetMapping("/zones-traitees")
    public List<ZoneTraiteeReponse> zonesTraitees() {
        return zones.lister();
    }

    /**
     * Declare une zone traitee.
     *
     * <p><strong>Aucune identite de tiers n'est demandee ni stockee</strong> : un
     * polygone, une date, une substance quand on la connait. Le §13 refuse un
     * annuaire de voisins, et ce refus tient.
     */
    @PostMapping("/zones-traitees")
    public ResponseEntity<ZoneTraiteeReponse> declarer(
            @Valid @RequestBody ZoneTraiteeCorps corps) {
        ZoneTraiteeReponse zone = zones.declarer(corps);
        return ResponseEntity
                .created(URI.create("/api/environnement/zones-traitees/" + zone.id()))
                .body(zone);
    }

    @DeleteMapping("/zones-traitees/{id}")
    public ResponseEntity<Void> supprimerZone(@PathVariable Long id) {
        zones.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Exposition d'un rucher aux zones DECLAREES.
     *
     * <p>La reponse porte le nombre de declarations et la date de la plus
     * recente, pour qu'« aucune zone a proximite » se lise « rien ne m'a ete
     * declare » — jamais « rien n'a ete epandu ». Une couche declarative est
     * incomplete par construction.
     */
    @GetMapping("/sites/{siteId}/exposition")
    public ExpositionRucher exposition(@PathVariable Long siteId) {
        return zones.autour(siteId, LocalDate.now());
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
