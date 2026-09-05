package com.zumm.controller;

import com.zumm.repository.NourrissementRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.service.ConformiteBioService;
import com.zumm.service.ElevageService;
import com.zumm.service.RegistreElevagePdfService;
import com.zumm.web.dto.DossierConformite;
import com.zumm.web.dto.Genealogie;
import com.zumm.web.dto.IndexGenetique;
import com.zumm.web.dto.NourrissementReponse;
import com.zumm.web.dto.ReineElevage;
import com.zumm.web.dto.ReineElevageCorps;
import com.zumm.web.dto.SerieCorps;
import com.zumm.web.dto.SerieReponse;
import com.zumm.web.dto.TraitementReponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * Elevage : reines, lignees, series et documents de controle (SPRINT-29, lot D).
 *
 * <p>Un prefixe propre, distinct de {@code /api/reines} qui sert depuis le
 * SPRINT-07 le JOURNAL d'une ruche. Les deux ressources coexistent parce
 * qu'elles ne parlent pas de la meme chose — l'une des evenements, l'autre des
 * individus — et fusionner leurs routes aurait casse un contrat public pour
 * economiser un segment d'URL.
 */
@RestController
@RequestMapping("/api/elevage")
public class ElevageController {

    /** Fenetre proposee aux documents quand l'appelant n'en donne pas : l'annee civile. */
    private static final int MOIS_PAR_DEFAUT = 12;

    private final ElevageService service;
    private final ConformiteBioService conformite;
    private final RegistreElevagePdfService pdf;
    private final TraitementRepository traitements;
    private final NourrissementRepository nourrissements;

    public ElevageController(ElevageService service, ConformiteBioService conformite,
            RegistreElevagePdfService pdf, TraitementRepository traitements,
            NourrissementRepository nourrissements) {
        this.service = service;
        this.conformite = conformite;
        this.pdf = pdf;
        this.traitements = traitements;
        this.nourrissements = nourrissements;
    }

    // ─── Reines ──────────────────────────────────────────────────────────────

    @GetMapping("/reines")
    public List<ReineElevage> lister() {
        return service.lister();
    }

    @GetMapping("/reines/{id}")
    public ReineElevage obtenir(@PathVariable Long id) {
        return service.obtenir(id);
    }

    @PostMapping("/reines")
    public ResponseEntity<ReineElevage> creer(@Valid @RequestBody ReineElevageCorps corps) {
        ReineElevage creee = service.creer(corps);
        return ResponseEntity.created(URI.create("/api/elevage/reines/" + creee.id())).body(creee);
    }

    @PutMapping("/reines/{id}")
    public ReineElevage mettreAJour(@PathVariable Long id,
            @Valid @RequestBody ReineElevageCorps corps) {
        return service.mettreAJour(id, corps);
    }

    @DeleteMapping("/reines/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /** Ascendance et descendance d'une reine. */
    @GetMapping("/reines/{id}/genealogie")
    public Genealogie genealogie(@PathVariable Long id) {
        return service.genealogie(id);
    }

    /**
     * Criteres observes pendant le regne d'une reine.
     *
     * <p>Aucune note globale n'est rendue : voir {@code IndexGenetiqueService}.
     */
    @GetMapping("/reines/{id}/index")
    public IndexGenetique index(@PathVariable Long id) {
        return service.index(id);
    }

    // ─── Series d'elevage ────────────────────────────────────────────────────

    @GetMapping("/series")
    public List<SerieReponse> series() {
        return service.listerSeries();
    }

    @PostMapping("/series")
    public ResponseEntity<SerieReponse> creerSerie(@Valid @RequestBody SerieCorps corps) {
        SerieReponse creee = service.creerSerie(corps);
        return ResponseEntity.created(URI.create("/api/elevage/series/" + creee.id())).body(creee);
    }

    @PutMapping("/series/{id}")
    public SerieReponse mettreAJourSerie(@PathVariable Long id,
            @Valid @RequestBody SerieCorps corps) {
        return service.mettreAJourSerie(id, corps);
    }

    @DeleteMapping("/series/{id}")
    public ResponseEntity<Void> supprimerSerie(@PathVariable Long id) {
        service.supprimerSerie(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Documents ───────────────────────────────────────────────────────────

    /**
     * Registre d'elevage reglementaire.
     *
     * <p>{@code inline} et non {@code attachment} : on le relit a l'ecran avant
     * de l'imprimer, comme la fiche d'inspection du SPRINT-24.
     */
    @GetMapping(value = "/registre.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> registre(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqu) {
        LocalDate fin = jusqu == null ? LocalDate.now() : jusqu;
        LocalDate debut = depuis == null ? fin.minusMonths(MOIS_PAR_DEFAUT) : depuis;
        List<TraitementReponse> actes = traitements
                .findByDateDebutBetweenOrderByDateDebutAscRuche_IdAsc(debut, fin).stream()
                .map(t -> TraitementReponse.de(t, LocalDate.now())).toList();
        List<NourrissementReponse> apports = nourrissements
                .findByDateApportBetweenOrderByDateApportAscRuche_IdAsc(debut, fin).stream()
                .map(NourrissementReponse::de).toList();
        return enLigne("registre-elevage", pdf.registre(debut, fin, actes, apports));
    }

    /** Points de controle, tels que le systeme peut les verifier — et pas au-dela. */
    @GetMapping("/conformite")
    public DossierConformite conformite(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqu) {
        LocalDate fin = jusqu == null ? LocalDate.now() : jusqu;
        LocalDate debut = depuis == null ? fin.minusMonths(MOIS_PAR_DEFAUT) : depuis;
        return conformite.evaluer(debut, fin);
    }

    @GetMapping(value = "/conformite.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> conformitePdf(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqu) {
        return enLigne("dossier-controle", pdf.dossier(conformite(depuis, jusqu)));
    }

    private ResponseEntity<byte[]> enLigne(String nom, byte[] document) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s.pdf\"".formatted(nom))
                .contentType(MediaType.APPLICATION_PDF)
                .body(document);
    }
}
