package com.zumm.controller;

import com.zumm.service.ComparaisonSitesService;
import com.zumm.service.SiteService;
import com.zumm.web.Pagination;
import com.zumm.web.dto.ComparaisonSite;
import com.zumm.web.dto.DemenagementCorps;
import com.zumm.web.dto.EmplacementReponse;
import com.zumm.web.dto.GrappeSites;
import com.zumm.web.dto.SiteCorps;
import com.zumm.web.dto.SiteReponse;
import com.zumm.web.dto.VoisinSite;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API CRUD des sites (US-003), avec recherche de proximite PostGIS.
 */
@RestController
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteService service;
    private final ComparaisonSitesService comparaisons;
    private final Pagination pagination;

    public SiteController(SiteService service, ComparaisonSitesService comparaisons,
            Pagination pagination) {
        this.service = service;
        this.comparaisons = comparaisons;
        this.pagination = pagination;
    }

    @PostMapping
    public ResponseEntity<SiteReponse> creer(@Valid @RequestBody SiteCorps corps) {
        SiteReponse reponse = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/sites/" + reponse.id())).body(reponse);
    }

    /**
     * Liste, paginee si le client le demande (US-052). Sans {@code page} ni
     * {@code taille}, le comportement est celui d'avant : la liste complete.
     * Le total est toujours porte par l'en-tete {@code X-Total-Count}.
     */
    @GetMapping
    public ResponseEntity<List<SiteReponse>> lister(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer taille,
            @RequestParam(required = false) String tri) {
        return pagination.reponse(page, taille, tri, service::lister, service::lister);
    }

    /**
     * Sites du tenant a moins de {@code rayonMetres} du point (latitude, longitude).
     * Exemple : {@code GET /api/sites/proches?latitude=45.1&longitude=1.2&rayonMetres=5000}.
     */
    @GetMapping("/proches")
    public List<SiteReponse> proches(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5000") double rayonMetres) {
        return service.proches(latitude, longitude, rayonMetres);
    }

    /**
     * Regroupement des sites du tenant par proximite (US-045).
     * Exemple : {@code GET /api/sites/grappes?distanceMetres=15000&minimumSites=2}.
     *
     * <p>{@code distanceMetres} est la distance reelle en-deca de laquelle deux sites
     * sont voisins ; {@code minimumSites} le nombre de voisins qu'il faut pour former
     * un noyau. Les sites qui n'en font partie d'aucun ressortent en grappe singleton.
     */
    @GetMapping("/grappes")
    public List<GrappeSites> grappes(
            @RequestParam(defaultValue = "15000") double distanceMetres,
            @RequestParam(defaultValue = "2") int minimumSites) {
        return service.grappes(distanceMetres, minimumSites);
    }

    /**
     * Compare des emplacements candidats sur leur POTENTIEL (SPRINT-23, lot B).
     *
     * <p>Exemple : {@code GET /api/sites/comparaison?ids=3,7,12}. Distincte de
     * {@code /grappes} et {@code /{id}/voisins}, qui comparent dans l'ESPACE :
     * celles-la disent qui est proche de quoi, celle-ci ce qu'on y recolte.
     */
    @GetMapping("/comparaison")
    public List<ComparaisonSite> comparaison(@RequestParam List<Long> ids) {
        return comparaisons.comparer(ids);
    }

    @GetMapping("/{id}")
    public SiteReponse obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    /**
     * Sites les plus proches d'un site donne, distance geodesique a l'appui (US-046).
     * Exemple : {@code GET /api/sites/12/voisins?limite=3}.
     */
    @GetMapping("/{id}/voisins")
    public List<VoisinSite> voisins(@PathVariable Long id,
                                    @RequestParam(defaultValue = "3") int limite) {
        return service.voisins(id, limite);
    }

    /**
     * Historique des emplacements occupes par un rucher (SPRINT-21, transhumance).
     * Exemple : {@code GET /api/sites/12/emplacements}.
     *
     * <p>Les positions y sont masquees comme partout ailleurs : la suite des
     * emplacements d'un rucher est une carte plus riche que sa position courante.
     */
    @GetMapping("/{id}/emplacements")
    public List<EmplacementReponse> emplacements(@PathVariable Long id) {
        return service.historique(id);
    }

    /**
     * Deplace le rucher : clot l'emplacement courant et en ouvre un nouveau
     * (SPRINT-21).
     *
     * <p>Distinct de {@code PUT /api/sites/{id}}, qui CORRIGE une position mal
     * saisie sans rien inscrire dans l'historique. Un {@code POST} parce que
     * l'operation cree une ressource — un emplacement — la ou le {@code PUT}
     * remplace un etat.
     */
    @PostMapping("/{id}/demenagement")
    public SiteReponse demenager(@PathVariable Long id,
            @Valid @RequestBody DemenagementCorps corps) {
        return service.demenager(id, corps);
    }

    @PutMapping("/{id}")
    public SiteReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody SiteCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
