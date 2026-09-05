package com.zumm.service;

import com.zumm.domain.Reine;
import com.zumm.domain.Ruche;
import com.zumm.domain.SerieElevage;
import com.zumm.repository.ReineRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SerieElevageRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.Genealogie;
import com.zumm.web.dto.IndexGenetique;
import com.zumm.web.dto.ReineElevage;
import com.zumm.web.dto.ReineElevageCorps;
import com.zumm.web.dto.SerieCorps;
import com.zumm.web.dto.SerieReponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reines, series d'elevage et genealogie (SPRINT-29, lot D).
 *
 * <p>Trois concurrents — BeeKube, APiLOG, HiveBook — font de la genealogie leur
 * argument central. Elle n'existait pas : {@code suivi_reine} est le journal
 * d'une RUCHE, et une cle reflexive dessus aurait relie des evenements.
 *
 * <p><strong>Ce service refuse les cycles</strong>, et c'est la seule regle que
 * la base ne peut pas tenir seule : {@code ck_reine_mere} verifie qu'une reine
 * n'est pas sa propre mere, mais pas qu'une arriere-grand-mere ne descend pas de
 * son arriere-petite-fille. Une lignee circulaire ferait boucler toute lecture
 * d'arbre — et une boucle en production ne se voit qu'au moment ou elle
 * s'installe.
 */
@Service
@Transactional
public class ElevageService {

    /**
     * Profondeur maximale explorée, dans les deux sens.
     *
     * <p>Une garde, pas une limite metier : quinze generations de reines
     * couvrent une vie d'apiculteur. Elle protege la lecture d'une base reprise
     * d'ailleurs, qui pourrait porter un cycle que ce service n'a pas ecrit.
     */
    private static final int PROFONDEUR_MAX = 15;

    private final ReineRepository reines;
    private final SerieElevageRepository series;
    private final RucheRepository ruches;
    private final IndexGenetiqueService index;

    public ElevageService(ReineRepository reines, SerieElevageRepository series,
            RucheRepository ruches, IndexGenetiqueService index) {
        this.reines = reines;
        this.series = series;
        this.ruches = ruches;
        this.index = index;
    }

    // ─── Reines ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ReineElevage> lister() {
        return reines.findAllByOrderByIdDesc().stream().map(ReineElevage::de).toList();
    }

    @Transactional(readOnly = true)
    public ReineElevage obtenir(Long id) {
        return ReineElevage.de(entite(id));
    }

    public ReineElevage creer(ReineElevageCorps corps) {
        if (corps.code() != null && !corps.code().isBlank() && reines.existsByCode(corps.code())) {
            throw new RequeteInvalide("Une reine porte deja le code « " + corps.code() + " ».");
        }
        Reine reine = new Reine(corps.origineOuInconnue());
        appliquer(reine, corps);
        return ReineElevage.de(reines.save(reine));
    }

    public ReineElevage mettreAJour(Long id, ReineElevageCorps corps) {
        Reine reine = entite(id);
        if (corps.code() != null && !corps.code().isBlank()
                && !corps.code().equals(reine.getCode()) && reines.existsByCode(corps.code())) {
            throw new RequeteInvalide("Une reine porte deja le code « " + corps.code() + " ».");
        }
        reine.setOrigine(corps.origineOuInconnue());
        appliquer(reine, corps);
        return ReineElevage.de(reine);
    }

    public void supprimer(Long id) {
        reines.delete(entite(id));
    }

    // ─── Genealogie ──────────────────────────────────────────────────────────

    /**
     * Ascendance et descendance d'une reine.
     *
     * <p>Deux parcours distincts, parce que les deux sens n'ont pas la meme
     * forme : les meres font une chaine, les filles un arbre. Les fondre aurait
     * force l'interface a deviner de quel cote elle se trouve.
     */
    @Transactional(readOnly = true)
    public Genealogie genealogie(Long id) {
        Reine reine = entite(id);
        return new Genealogie(ReineElevage.de(reine), ascendants(reine), descendants(reine));
    }

    private List<Genealogie.Noeud> ascendants(Reine depart) {
        List<Genealogie.Noeud> chaine = new ArrayList<>();
        Set<Long> vues = new HashSet<>();
        vues.add(depart.getId());
        Reine courante = depart.getMere();
        int profondeur = 1;
        // `vues` autant que `PROFONDEUR_MAX` : la garde de profondeur suffirait a
        // ne pas boucler indefiniment, mais elle rendrait quinze fois la meme
        // reine. La visite deja faite arrete proprement.
        while (courante != null && profondeur <= PROFONDEUR_MAX && vues.add(courante.getId())) {
            chaine.add(noeud(courante, profondeur,
                    courante.getMere() == null ? null : courante.getMere().getId()));
            courante = courante.getMere();
            profondeur++;
        }
        return chaine;
    }

    private List<Genealogie.Noeud> descendants(Reine depart) {
        List<Genealogie.Noeud> arbre = new ArrayList<>();
        Set<Long> vues = new HashSet<>();
        vues.add(depart.getId());
        // Parcours en largeur : les filles avant les petites-filles, ce qui rend
        // la liste directement dessinable de haut en bas.
        Deque<Reine> file = new ArrayDeque<>();
        Deque<Integer> profondeurs = new ArrayDeque<>();
        file.add(depart);
        profondeurs.add(0);
        while (!file.isEmpty()) {
            Reine mere = file.removeFirst();
            int profondeur = profondeurs.removeFirst();
            if (profondeur >= PROFONDEUR_MAX) {
                continue;
            }
            for (Reine fille : reines.findByMere_IdOrderByIdAsc(mere.getId())) {
                if (!vues.add(fille.getId())) {
                    continue;
                }
                arbre.add(noeud(fille, profondeur + 1, mere.getId()));
                file.add(fille);
                profondeurs.add(profondeur + 1);
            }
        }
        return arbre;
    }

    private static Genealogie.Noeud noeud(Reine r, int profondeur, Long parentId) {
        return new Genealogie.Noeud(r.getId(), ReineElevage.nom(r), profondeur, parentId,
                r.getRace(), r.getAnneeNaissance(), r.getStatut());
    }

    /**
     * Criteres observes pendant le regne d'une reine.
     *
     * <p>Le calcul vit dans {@link IndexGenetiqueService} — il lit quatre
     * registres que ce service-ci n'a aucune raison de connaitre —, mais la
     * porte d'entree reste la reine, qui est ici.
     */
    @Transactional(readOnly = true)
    public IndexGenetique index(Long id) {
        return index.pour(entite(id));
    }

    // ─── Series ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SerieReponse> listerSeries() {
        return series.findAllByOrderByDateGreffageDescIdDesc().stream()
                .map(SerieReponse::de).toList();
    }

    public SerieReponse creerSerie(SerieCorps corps) {
        if (series.existsByNom(corps.nom())) {
            throw new RequeteInvalide("Une serie porte deja le nom « " + corps.nom() + " ».");
        }
        SerieElevage serie = new SerieElevage(corps.nom(), corps.dateGreffage(),
                corps.nbGreffees());
        appliquerSerie(serie, corps);
        return SerieReponse.de(series.save(serie));
    }

    public SerieReponse mettreAJourSerie(Long id, SerieCorps corps) {
        SerieElevage serie = series.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Serie", id));
        if (!serie.getNom().equals(corps.nom()) && series.existsByNom(corps.nom())) {
            throw new RequeteInvalide("Une serie porte deja le nom « " + corps.nom() + " ».");
        }
        serie.setNom(corps.nom());
        serie.setDateGreffage(corps.dateGreffage());
        serie.setNbGreffees(corps.nbGreffees());
        appliquerSerie(serie, corps);
        return SerieReponse.de(serie);
    }

    public void supprimerSerie(Long id) {
        series.delete(series.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Serie", id)));
    }

    // ─── Interne ─────────────────────────────────────────────────────────────

    private void appliquer(Reine reine, ReineElevageCorps corps) {
        reine.setCode(corps.code() == null || corps.code().isBlank() ? null : corps.code().trim());
        reine.setMere(mereValidee(reine, corps.mereId()));
        reine.setRucheMere(rucheEventuelle(corps.rucheMereId()));
        reine.setRuche(rucheEventuelle(corps.rucheId()));
        reine.setSerie(corps.serieId() == null ? null : series.findById(corps.serieId())
                .orElseThrow(() -> new RequeteInvalide(
                        "Serie inconnue dans ce tenant : " + corps.serieId())));
        // Le fournisseur n'a de sens que sur une reine achetee — la base le
        // refuse. On l'efface plutot que de faire echouer une saisie ou
        // l'utilisateur a simplement change d'origine apres coup.
        reine.setFournisseur("achat".equals(reine.getOrigine()) ? corps.fournisseur() : null);
        reine.setRace(corps.race());
        reine.setAnneeNaissance(corps.anneeNaissance());
        reine.setCouleurMarquage(corps.couleurMarquage());
        reine.setAilesClippees(corps.ailesClippees());
        reine.setDateGreffage(corps.dateGreffage());
        reine.setDateNaissance(corps.dateNaissance());
        reine.setDateFecondation(corps.dateFecondation());
        reine.setDateIntroduction(corps.dateIntroduction());
        reine.setDateFin(corps.dateFin());
        reine.setStatut(corps.statutOuEnService());
        reine.setNote(corps.note());
    }

    /**
     * Resout la mere, en refusant de fermer une boucle.
     *
     * <p>Le cycle se cherche en remontant depuis la mere proposee : si l'on
     * retombe sur la reine elle-meme, l'arbre deviendrait circulaire. La base
     * n'en verifie qu'un pas ({@code ck_reine_mere}), et ce cas-ci se produit
     * bel et bien — on corrige une filiation saisie a l'envers.
     */
    private Reine mereValidee(Reine reine, Long mereId) {
        if (mereId == null) {
            return null;
        }
        Reine mere = reines.findById(mereId).orElseThrow(() ->
                new RequeteInvalide("Reine mere inconnue dans ce tenant : " + mereId));
        if (reine.getId() != null) {
            Reine aieule = mere;
            for (int pas = 0; aieule != null && pas <= PROFONDEUR_MAX; pas++) {
                if (reine.getId().equals(aieule.getId())) {
                    throw new RequeteInvalide(
                            "Cette filiation ferait descendre la reine d'elle-meme.");
                }
                aieule = aieule.getMere();
            }
        }
        return mere;
    }

    private void appliquerSerie(SerieElevage serie, SerieCorps corps) {
        serie.setSouche(corps.soucheId() == null ? null : reines.findById(corps.soucheId())
                .orElseThrow(() -> new RequeteInvalide(
                        "Reine souche inconnue dans ce tenant : " + corps.soucheId())));
        serie.setRucheEleveuse(rucheEventuelle(corps.rucheEleveuseId()));
        serie.setMethode(corps.methode());
        serie.setNbAcceptees(corps.nbAcceptees());
        serie.setNbNees(corps.nbNees());
        serie.setNbFecondees(corps.nbFecondees());
        serie.setNote(corps.note());
    }

    private Ruche rucheEventuelle(Long id) {
        return id == null ? null : ruches.findById(id).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + id));
    }

    Reine entite(Long id) {
        return reines.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Reine", id));
    }
}
