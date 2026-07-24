package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.EtatSante;
import com.zumm.domain.Mesure;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.domain.Visite;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.AlerteSanitaire;
import com.zumm.web.dto.CalendrierCellule;
import com.zumm.web.dto.CalendrierCellule.VisiteBreve;
import com.zumm.web.dto.LigneProduction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregations des trois vues de pilotage du SPRINT-05 : calendrier matriciel
 * (US-012), tableau de bord production (US-013) et alertes sanitaires (US-014).
 *
 * <p>Les agregations sont faites en memoire a partir de lectures filtrees par le
 * tenant courant (@TenantId + RLS). Les volumes restent modestes a ce stade — le
 * modele capteurs est en preparation (EPIC-004) ; une bascule vers des agregations
 * SQL/continuous aggregates TimescaleDB est prevue quand les mesures afflueront.
 */
@Service
@Transactional(readOnly = true)
public class TableauDeBordService {

    private final VisiteRepository visites;
    private final MesureRepository mesures;
    private final RucheRepository ruches;
    private final ConfigurationMetier configuration;

    public TableauDeBordService(VisiteRepository visites, MesureRepository mesures,
            RucheRepository ruches, ConfigurationMetier configuration) {
        this.visites = visites;
        this.mesures = mesures;
        this.ruches = ruches;
        this.configuration = configuration;
    }

    // ─── US-012 : calendrier matriciel agents × ruches ─────────────────────────

    /** Cellules (agent × ruche) des visites tombant dans [debut, fin]. */
    public List<CalendrierCellule> calendrier(LocalDate debut, LocalDate fin) {
        Map<List<Long>, List<Visite>> parCouple = new LinkedHashMap<>();
        for (Visite v : visites.findByDateVisiteBetweenOrderByDateVisiteAsc(debut, fin)) {
            parCouple.computeIfAbsent(List.of(v.getAgent().getId(), v.getRuche().getId()),
                    cle -> new ArrayList<>()).add(v);
        }
        List<CalendrierCellule> cellules = new ArrayList<>();
        for (List<Visite> groupe : parCouple.values()) {
            Visite ref = groupe.get(0);
            cellules.add(new CalendrierCellule(
                    ref.getAgent().getId(), ref.getAgent().getNom(),
                    ref.getRuche().getId(), ref.getRuche().getModele(),
                    groupe.size(), groupe.stream().map(VisiteBreve::de).toList()));
        }
        return cellules;
    }

    // ─── US-013 : tableau de bord production ───────────────────────────────────

    /** Synthese du poids par ruche, avec drapeau {@code sousSeuil} et productivite moyenne. */
    public List<LigneProduction> production() {
        int seuilKg = configuration.seuils().poidsRucheAlerteKg();
        Map<Long, double[]> productiviteParRuche = productiviteMoyenneParRuche();
        Map<Long, AgregatPoids> poids = agregatPoidsParRuche();

        List<LigneProduction> lignes = new ArrayList<>();
        for (Ruche ruche : ruches.findAll()) {
            AgregatPoids p = poids.get(ruche.getId());
            double[] prod = productiviteParRuche.get(ruche.getId());
            Double productiviteMoyenne = prod == null ? null
                    : arrondi2(BigDecimal.valueOf(prod[0] / prod[1])).doubleValue();
            if (p == null) {
                lignes.add(new LigneProduction(ruche.getId(), ruche.getModele(),
                        null, null, null, 0, false, productiviteMoyenne));
                continue;
            }
            boolean sousSeuil = p.actuel.compareTo(BigDecimal.valueOf(seuilKg)) < 0;
            lignes.add(new LigneProduction(ruche.getId(), ruche.getModele(),
                    arrondi2(p.actuel), arrondi2(p.min), arrondi2(p.max), p.nombre,
                    sousSeuil, productiviteMoyenne));
        }
        return lignes;
    }

    private Map<Long, AgregatPoids> agregatPoidsParRuche() {
        Map<Long, AgregatPoids> parRuche = new LinkedHashMap<>();
        // Triees par ruche puis instant croissant : la derniere vue est la plus recente.
        for (Mesure m : mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS)) {
            parRuche.computeIfAbsent(m.getId().getRucheId(), cle -> new AgregatPoids())
                    .ajouter(m.getValeur());
        }
        return parRuche;
    }

    private Map<Long, double[]> productiviteMoyenneParRuche() {
        Map<Long, double[]> parRuche = new LinkedHashMap<>();
        for (Visite v : visites.findAll()) {
            if (v.getProductivite() == null) {
                continue;
            }
            double[] cumul = parRuche.computeIfAbsent(v.getRuche().getId(), cle -> new double[2]);
            cumul[0] += v.getProductivite();
            cumul[1] += 1;
        }
        return parRuche;
    }

    // ─── US-014 : tableau de bord alertes sanitaires ───────────────────────────

    /** Une alerte par ruche, hierarchisee, ruches critiques d'abord. */
    public List<AlerteSanitaire> alertesSanitaires() {
        int delaiJours = configuration.seuils().delaiAlerteJours();
        LocalDate aujourdhui = LocalDate.now();

        // Derniere visite par ruche (liste triee par date croissante : on garde la derniere).
        Map<Long, Visite> derniereParRuche = new LinkedHashMap<>();
        for (Visite v : visites.findAllByOrderByDateVisiteAsc()) {
            derniereParRuche.put(v.getRuche().getId(), v);
        }

        List<AlerteSanitaire> alertes = new ArrayList<>();
        for (Ruche ruche : ruches.findAll()) {
            Visite derniere = derniereParRuche.get(ruche.getId());
            alertes.add(evaluer(ruche, derniere, aujourdhui, delaiJours));
        }
        alertes.sort(Comparator.comparingInt(TableauDeBordService::rang));
        return alertes;
    }

    private AlerteSanitaire evaluer(Ruche ruche, Visite derniere, LocalDate aujourdhui, int delaiJours) {
        if (derniere == null) {
            return new AlerteSanitaire(ruche.getId(), ruche.getModele(), null, null, null,
                    AlerteSanitaire.CRITIQUE, "Aucune visite enregistrée");
        }
        long jours = ChronoUnit.DAYS.between(derniere.getDateVisite(), aujourdhui);
        EtatSante etat = derniere.getEtatSante();
        String niveau;
        String motif;
        if (etat == EtatSante.MAUVAIS) {
            niveau = AlerteSanitaire.CRITIQUE;
            motif = "État sanitaire mauvais à la dernière visite";
        } else if (jours > delaiJours) {
            niveau = AlerteSanitaire.ATTENTION;
            motif = "Aucune visite depuis " + jours + " jours (seuil " + delaiJours + ")";
        } else if (etat == EtatSante.MOYEN) {
            niveau = AlerteSanitaire.ATTENTION;
            motif = "État sanitaire moyen à surveiller";
        } else {
            niveau = AlerteSanitaire.OK;
            motif = "État sanitaire satisfaisant";
        }
        return new AlerteSanitaire(ruche.getId(), ruche.getModele(), etat,
                derniere.getDateVisite(), jours, niveau, motif);
    }

    private static int rang(AlerteSanitaire a) {
        return switch (a.niveau()) {
            case AlerteSanitaire.CRITIQUE -> 0;
            case AlerteSanitaire.ATTENTION -> 1;
            default -> 2;
        };
    }

    private static BigDecimal arrondi2(BigDecimal valeur) {
        return valeur == null ? null : valeur.setScale(2, RoundingMode.HALF_UP);
    }

    /** Accumulateur du poids d'une ruche : min, max, nombre et derniere valeur (courante). */
    private static final class AgregatPoids {
        private BigDecimal min;
        private BigDecimal max;
        private BigDecimal actuel;
        private long nombre;

        void ajouter(BigDecimal valeur) {
            if (min == null || valeur.compareTo(min) < 0) {
                min = valeur;
            }
            if (max == null || valeur.compareTo(max) > 0) {
                max = valeur;
            }
            actuel = valeur; // Flux trie par instant croissant : la derniere ajoutee est la plus recente.
            nombre++;
        }
    }
}
