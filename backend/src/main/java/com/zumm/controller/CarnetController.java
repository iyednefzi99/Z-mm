package com.zumm.controller;

import com.zumm.service.CarnetService;
import com.zumm.web.dto.GabaritCorps;
import com.zumm.web.dto.GabaritReponse;
import com.zumm.web.dto.PointReferentiel;
import com.zumm.web.dto.ProduitReferentiel;
import com.zumm.web.dto.StatistiquePoint;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Le carnet parametrable (SPRINT-28, lot I).
 *
 * <p>Un controleur pour un concept, plutot que quatre pour quatre tables : les
 * referentiels, les gabarits et les statistiques d'observation ne se lisent
 * qu'ensemble — un gabarit sans son referentiel est une liste de codes, et une
 * statistique sans libelle n'est pas lisible. Le prefixe commun donne aussi une
 * regle RBAC unique, ce qui evite qu'une route s'en echappe par oubli.
 */
@RestController
@RequestMapping("/api/carnet")
public class CarnetController {

    /** Fenetre proposee aux statistiques quand l'appelant n'en donne pas. */
    private static final int JOURS_PAR_DEFAUT = 365;

    private final CarnetService service;

    public CarnetController(CarnetService service) {
        this.service = service;
    }

    /** Referentiel ferme des points d'observation. */
    @GetMapping("/points")
    public List<PointReferentiel> points() {
        return service.points();
    }

    /**
     * Referentiel indicatif des produits de traitement.
     *
     * <p>Il pre-remplit la saisie ; la notice fait foi. C'est le sens du champ
     * {@code mention}, qui accompagne chaque ligne et doit rester affiche.
     */
    @GetMapping("/produits")
    public List<ProduitReferentiel> produits() {
        return service.produits();
    }

    @GetMapping("/gabarits")
    public List<GabaritReponse> listerGabarits() {
        return service.listerGabarits();
    }

    @GetMapping("/gabarits/{id}")
    public GabaritReponse obtenirGabarit(@PathVariable Long id) {
        return service.obtenirGabarit(id);
    }

    @PostMapping("/gabarits")
    public ResponseEntity<GabaritReponse> creer(@Valid @RequestBody GabaritCorps corps) {
        GabaritReponse cree = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/carnet/gabarits/" + cree.id())).body(cree);
    }

    @PutMapping("/gabarits/{id}")
    public GabaritReponse mettreAJour(@PathVariable Long id,
            @Valid @RequestBody GabaritCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/gabarits/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Ce que chaque point d'observation a donne sur une periode.
     *
     * <p>Exemple : {@code GET /api/carnet/statistiques?depuis=2026-01-01&jusqu=2026-09-04}.
     * Sans parametre, la fenetre est l'annee ecoulee — bornee, parce qu'une
     * requete sans borne balaierait toute l'histoire de l'exploitation a chaque
     * ouverture d'ecran.
     */
    @GetMapping("/statistiques")
    public List<StatistiquePoint> statistiques(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqu) {
        LocalDate fin = jusqu == null ? LocalDate.now() : jusqu;
        LocalDate debut = depuis == null ? fin.minusDays(JOURS_PAR_DEFAUT) : depuis;
        return service.statistiques(debut, fin);
    }
}
