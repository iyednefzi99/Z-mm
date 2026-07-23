package com.zumm.controller;

import com.zumm.service.FermierService;
import com.zumm.web.dto.FermierCorps;
import com.zumm.web.dto.FermierReponse;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * API CRUD des fermiers (US-001).
 *
 * <p>Toutes les operations sont implicitement restreintes au tenant du jeton :
 * aucun identifiant de tenant ne transite par l'API. L'acces exige un jeton
 * valide (securite fermee par defaut) ; la matrice RBAC par role (US-022) est un
 * raffinement ulterieur.
 */
@RestController
@RequestMapping("/api/fermiers")
public class FermierController {

    private final FermierService service;

    public FermierController(FermierService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FermierReponse> creer(@Valid @RequestBody FermierCorps corps) {
        FermierReponse reponse = FermierReponse.de(service.creer(corps));
        return ResponseEntity.created(URI.create("/api/fermiers/" + reponse.id())).body(reponse);
    }

    @GetMapping
    public List<FermierReponse> lister() {
        return service.lister().stream().map(FermierReponse::de).toList();
    }

    @GetMapping("/{id}")
    public FermierReponse obtenir(@PathVariable Long id) {
        return FermierReponse.de(service.obtenir(id));
    }

    @PutMapping("/{id}")
    public FermierReponse mettreAJour(@PathVariable Long id, @Valid @RequestBody FermierCorps corps) {
        return FermierReponse.de(service.mettreAJour(id, corps));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
