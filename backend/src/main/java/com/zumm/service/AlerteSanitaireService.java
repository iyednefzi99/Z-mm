package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.EtatSante;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.AlerteSanitaire;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertes sanitaires par ruche (US-014).
 *
 * <p>Depuis le SPRINT-17, la derniere visite de chaque ruche vient d'une seule
 * requete, la ou l'implementation precedente lisait l'historique entier de
 * l'exploitation pour n'en garder qu'une ligne sur mille.
 *
 * <p>La hierarchie des motifs est volontairement ordonnee : un etat sanitaire
 * MAUVAIS prime sur un retard de visite, parce qu'un probleme constate est plus
 * urgent qu'un controle a faire. L'ordre des branches EST la regle metier — le
 * modifier change le comportement, pas seulement la lisibilite.
 */
@Service
@Transactional(readOnly = true)
public class AlerteSanitaireService {

    private final VisiteRepository visites;
    private final RucheRepository ruches;
    private final ConfigurationMetier configuration;

    public AlerteSanitaireService(VisiteRepository visites, RucheRepository ruches,
            ConfigurationMetier configuration) {
        this.visites = visites;
        this.ruches = ruches;
        this.configuration = configuration;
    }

    /** Une alerte par ruche, hierarchisee, ruches critiques d'abord. */
    public List<AlerteSanitaire> alertesSanitaires() {
        int delaiJours = configuration.seuils().delaiAlerteJours();
        LocalDate aujourdhui = LocalDate.now();

        Map<Long, Visite> derniereParRuche = visites.dernieresVisitesParRuche().stream()
                .collect(Collectors.toMap(v -> v.getRuche().getId(), Function.identity()));

        List<AlerteSanitaire> alertes = new ArrayList<>();
        for (Ruche ruche : ruches.findAll()) {
            alertes.add(evaluer(ruche, derniereParRuche.get(ruche.getId()), aujourdhui, delaiJours));
        }
        alertes.sort(Comparator.comparingInt(AlerteSanitaireService::rang));
        return alertes;
    }

    private AlerteSanitaire evaluer(Ruche ruche, Visite derniere, LocalDate aujourdhui,
            int delaiJours) {
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
}
