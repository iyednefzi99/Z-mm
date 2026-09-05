package com.zumm.controller;

import com.zumm.domain.Photo;
import com.zumm.service.PhotoService;
import com.zumm.web.dto.PhotoCibleCorps;
import com.zumm.web.dto.PhotoReponse;
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
 * Photos attachees aux objets du parc (SPRINT-21).
 *
 * <p>Une ressource unique plutot que quatre sous-ressources
 * ({@code /api/ruches/{id}/photos}, {@code /api/sites/{id}/photos}...) : le
 * traitement est identique dans les six cas, et autant de controleurs jumeaux
 * auraient multiplie la surface a auditer pour la meme fonction.
 *
 * <p>{@code POST /api/visites/{id}/photos} reste servi par {@code VisiteController} :
 * il fonctionne et il est utilise ; le doubler ici aurait casse un contrat.
 */
@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PhotoReponse> attacher(@Valid @RequestBody PhotoCibleCorps corps) {
        PhotoReponse reponse = service.attacher(corps);
        return ResponseEntity.created(URI.create("/api/photos/" + reponse.id())).body(reponse);
    }

    /** Exemple : {@code GET /api/photos?cible=RUCHE&cibleId=12}. */
    @GetMapping
    public List<PhotoReponse> lister(@RequestParam Photo.Cible cible, @RequestParam Long cibleId) {
        return service.lister(cible, cibleId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
