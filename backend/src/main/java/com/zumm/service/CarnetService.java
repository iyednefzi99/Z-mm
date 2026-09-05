package com.zumm.service;

import com.zumm.domain.GabaritInspection;
import com.zumm.domain.GabaritPoint;
import com.zumm.domain.PointObservation;
import com.zumm.repository.GabaritInspectionRepository;
import com.zumm.repository.GabaritPointRepository;
import com.zumm.repository.PointObservationRepository;
import com.zumm.repository.ProduitTraitementRepository;
import com.zumm.repository.ReleveObservationRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.GabaritCorps;
import com.zumm.web.dto.GabaritReponse;
import com.zumm.web.dto.PointReferentiel;
import com.zumm.web.dto.ProduitReferentiel;
import com.zumm.web.dto.StatistiquePoint;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le carnet parametrable : referentiels, gabarits, statistiques (SPRINT-28).
 *
 * <p>Ferme quatre lignes du §3 de {@code docs/ECART-CONCURRENTS.md} — gabarits
 * reutilisables, saisie par cases a cocher, referentiel de traitements
 * pre-renseigne, ordonnances veterinaires.
 *
 * <p><strong>Le referentiel est ferme, et c'est tenu par la base.</strong> Ce
 * service LIT {@code point_observation} et {@code produit_traitement} ; il ne
 * les ecrit jamais, et n'a aucun moyen de le faire — la V28 retire le DML au
 * role applicatif. Un formulaire parametrable qui accepterait des champs libres
 * detruirait la statistique : dix exploitations inventeraient dix libelles pour
 * la meme observation. On active des cases existantes ; on n'en invente pas.
 */
@Service
@Transactional
public class CarnetService {

    private final PointObservationRepository points;
    private final ProduitTraitementRepository produits;
    private final GabaritInspectionRepository gabarits;
    private final GabaritPointRepository gabaritPoints;
    private final ReleveObservationRepository releves;

    public CarnetService(PointObservationRepository points, ProduitTraitementRepository produits,
            GabaritInspectionRepository gabarits, GabaritPointRepository gabaritPoints,
            ReleveObservationRepository releves) {
        this.points = points;
        this.produits = produits;
        this.gabarits = gabarits;
        this.gabaritPoints = gabaritPoints;
        this.releves = releves;
    }

    // ─── Referentiels ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PointReferentiel> points() {
        return points.findAllByOrderByOrdreAsc().stream().map(PointReferentiel::de).toList();
    }

    @Transactional(readOnly = true)
    public List<ProduitReferentiel> produits() {
        return produits.findAllByOrderByNomAsc().stream().map(ProduitReferentiel::de).toList();
    }

    // ─── Gabarits ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<GabaritReponse> listerGabarits() {
        return gabarits.findAllByOrderByNomAsc().stream().map(this::avecPoints).toList();
    }

    @Transactional(readOnly = true)
    public GabaritReponse obtenirGabarit(Long id) {
        return avecPoints(entite(id));
    }

    /**
     * Le gabarit propose par defaut, ou {@code null} s'il n'y en a pas.
     *
     * <p>Rend {@code null} plutot que de fabriquer un gabarit implicite : une
     * exploitation qui n'a jamais ouvert l'ecran de configuration doit garder
     * exactement la grille du SPRINT-20, et un gabarit invente le lui ferait
     * perdre sans qu'elle ait rien demande.
     */
    @Transactional(readOnly = true)
    public GabaritReponse gabaritParDefaut() {
        return gabarits.findByParDefautTrue()
                .filter(GabaritInspection::isActif)
                .map(this::avecPoints)
                .orElse(null);
    }

    public GabaritReponse creer(GabaritCorps corps) {
        if (gabarits.existsByNom(corps.nom())) {
            throw new RequeteInvalide("Un gabarit porte deja le nom « " + corps.nom() + " ».");
        }
        GabaritInspection gabarit = new GabaritInspection(corps.nom());
        appliquer(gabarit, corps);
        GabaritInspection enregistre = gabarits.save(gabarit);
        return GabaritReponse.de(enregistre, remplacerPoints(enregistre, corps.pointsOuVide()));
    }

    public GabaritReponse mettreAJour(Long id, GabaritCorps corps) {
        GabaritInspection gabarit = entite(id);
        if (!gabarit.getNom().equals(corps.nom()) && gabarits.existsByNom(corps.nom())) {
            throw new RequeteInvalide("Un gabarit porte deja le nom « " + corps.nom() + " ».");
        }
        gabarit.setNom(corps.nom());
        appliquer(gabarit, corps);
        return GabaritReponse.de(gabarit, remplacerPoints(gabarit, corps.pointsOuVide()));
    }

    /**
     * Supprime un gabarit.
     *
     * <p>La suppression est franche — {@code gabarit_point} suit en cascade —
     * parce qu'un gabarit ne laisse aucune trace dans les visites : le releve
     * pointe le REFERENTIEL, jamais le gabarit qui l'a propose. Une exploitation
     * qui reorganise son carnet ne doit pas rendre illisibles ses inspections
     * passees, et c'est ce choix de cle etrangere qui l'en empeche.
     */
    public void supprimer(Long id) {
        gabarits.delete(entite(id));
    }

    // ─── Statistiques ────────────────────────────────────────────────────────

    /**
     * Ce que chaque point a donne entre deux dates.
     *
     * <p>Sans borne, la requete balaierait toute l'histoire de l'exploitation a
     * chaque ouverture d'ecran. Les bornes sont donc obligatoires, et le
     * controleur en propose de raisonnables plutot que de les rendre facultatives.
     */
    @Transactional(readOnly = true)
    public List<StatistiquePoint> statistiques(LocalDate depuis, LocalDate jusqu) {
        if (depuis == null || jusqu == null || depuis.isAfter(jusqu)) {
            throw new RequeteInvalide("La periode doit etre bornee, et commencer avant de finir.");
        }
        return releves.statistiques(depuis, jusqu);
    }

    // ─── Interne ─────────────────────────────────────────────────────────────

    private void appliquer(GabaritInspection gabarit, GabaritCorps corps) {
        gabarit.setDescription(corps.description());
        gabarit.setNoyauCouvain(GabaritCorps.ouVrai(corps.noyauCouvain()));
        gabarit.setNoyauReine(GabaritCorps.ouVrai(corps.noyauReine()));
        gabarit.setNoyauCadres(GabaritCorps.ouVrai(corps.noyauCadres()));
        gabarit.setNoyauTemperament(GabaritCorps.ouVrai(corps.noyauTemperament()));
        gabarit.setActif(GabaritCorps.ouVrai(corps.actif()));

        boolean defaut = Boolean.TRUE.equals(corps.parDefaut());
        if (defaut) {
            // L'index unique partiel `uq_gabarit_defaut` interdit deux gabarits
            // par defaut. On bascule l'ancien AVANT de poser le nouveau, et on
            // vide la session pour que l'ordre des ordres SQL suive celui-ci —
            // sans ce `flush`, Hibernate peut inserer avant de mettre a jour, et
            // la contrainte refuse une transition pourtant legitime.
            gabarits.findByParDefautTrue()
                    .filter(ancien -> !ancien.equals(gabarit))
                    .ifPresent(ancien -> {
                        ancien.setParDefaut(false);
                        gabarits.saveAndFlush(ancien);
                    });
        }
        gabarit.setParDefaut(defaut);
    }

    /**
     * Reecrit la liste des points d'un gabarit.
     *
     * <p>Remplacement complet plutot que fusion, comme pour les pathologies
     * d'une visite : une fusion rendrait impossible de RETIRER un point, et un
     * carnet dont on ne peut qu'ajouter des cases finit par n'etre plus rempli.
     */
    private List<String> remplacerPoints(GabaritInspection gabarit, List<String> codes) {
        gabaritPoints.deleteAll(gabaritPoints.findByIdGabaritIdOrderByOrdreAsc(gabarit.getId()));
        gabaritPoints.flush();
        if (codes.isEmpty()) {
            return List.of();
        }
        Set<String> connus = Set.copyOf(points.findAllById(codes).stream()
                .map(PointObservation::getCode).toList());
        List<String> retenus = codes.stream().distinct().toList();
        for (String code : retenus) {
            if (!connus.contains(code)) {
                // La cle etrangere le refuserait aussi, mais en 500. Le
                // referentiel etant ferme, un code inconnu est une faute de
                // frappe du client, pas une panne du serveur.
                throw new RequeteInvalide("Point d'observation inconnu : " + code);
            }
        }
        for (int i = 0; i < retenus.size(); i++) {
            gabaritPoints.save(new GabaritPoint(gabarit.getId(), retenus.get(i), i));
        }
        return retenus;
    }

    private GabaritReponse avecPoints(GabaritInspection gabarit) {
        return GabaritReponse.de(gabarit,
                gabaritPoints.findByIdGabaritIdOrderByOrdreAsc(gabarit.getId()).stream()
                        .map(p -> p.getId().getPointCode())
                        .toList());
    }

    private GabaritInspection entite(Long id) {
        return gabarits.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Gabarit", id));
    }
}
