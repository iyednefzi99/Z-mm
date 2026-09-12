package com.zumm.service;

import com.zumm.domain.Consommable;
import com.zumm.domain.Tache;
import com.zumm.repository.TacheRepository;
import com.zumm.web.dto.EtapeTournee;
import com.zumm.web.dto.FeuilleChargement;
import com.zumm.web.dto.FeuilleChargement.BesoinConsommable;
import com.zumm.web.dto.FeuilleChargement.EtapeChargement;
import com.zumm.web.dto.TourneeReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feuille de chargement d'une tournee (SPRINT-33, lot K).
 *
 * <p>Ferme la moitie « logistique et chaine d'approvisionnement » de la ligne
 * « coordination d'equipes terrain, logistique multi-sites » (§12), 🟡 depuis le
 * SPRINT-23.
 *
 * <p><strong>Ce service n'invente rien, il assemble.</strong> La tournee vient
 * de {@link PlanningService#tournee}, les taches du registre, le stock de
 * {@code consommable}. Le seul ajout du lot est la colonne qui relie une tache a
 * ce qu'elle preleve — et c'est elle qui manquait, pas un calcul.
 *
 * <p><strong>La transaction est indispensable</strong>, comme partout dans ce
 * depot ou l'on serialise une entite : {@code Tache.ruche},
 * {@code Ruche.site} et {@code Tache.consommable} sont {@code LAZY}. Un mapping
 * fait depuis le controleur reussirait sur une liste vide et echouerait des la
 * premiere tache reelle — le defaut trouve au SPRINT-31 sur
 * {@code GET /api/mesures/alertes}.
 */
@Service
public class ChargementService {

    private final PlanningService plannings;
    private final TacheRepository taches;

    public ChargementService(PlanningService plannings, TacheRepository taches) {
        this.plannings = plannings;
        this.taches = taches;
    }

    /**
     * Ce qu'il faut charger pour la tournee d'un agent, un jour donne.
     *
     * <p>Les taches retenues sont celles qui sont <strong>ouvertes</strong> et
     * <strong>echues au plus tard ce jour-la</strong>, sur les ruches des ruchers
     * de la tournee. Prendre les taches a venir remplirait le vehicule de ce
     * qu'on ne fera pas ; ignorer les taches en retard laisserait a la maison
     * precisement ce qui aurait du partir la semaine derniere.
     */
    @Transactional(readOnly = true)
    public FeuilleChargement pour(Long agentId, LocalDate date, Long departSiteId) {
        TourneeReponse tournee = plannings.tournee(agentId, date, departSiteId);

        Map<Long, List<Tache>> parSite = new LinkedHashMap<>();
        for (Tache tache : taches
                .findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(date)) {
            if (tache.getRuche() == null || tache.getRuche().getSite() == null) {
                continue;
            }
            parSite.computeIfAbsent(tache.getRuche().getSite().getId(), cle -> new ArrayList<>())
                    .add(tache);
        }

        List<EtapeChargement> etapes = new ArrayList<>();
        // L'ordre d'insertion est celui de la tournee : la consolidation ci-dessous
        // en herite, et la feuille se lit dans l'ordre ou l'on charge.
        Map<Long, Cumul> cumul = new LinkedHashMap<>();
        for (EtapeTournee etape : tournee.etapes()) {
            List<Tache> surPlace = parSite.getOrDefault(etape.siteId(), List.of());
            etapes.add(new EtapeChargement(
                    etape.ordre(),
                    etape.siteId(),
                    etape.siteNom(),
                    etape.nombreVisites(),
                    surPlace.stream().map(Tache::getLibelle).toList(),
                    besoins(surPlace, new LinkedHashMap<>())));
            besoins(surPlace, cumul);
        }

        List<BesoinConsommable> totaux = cumul.values().stream().map(Cumul::figer).toList();
        return new FeuilleChargement(tournee.agentId(), tournee.agentNom(), date,
                tournee.nombreSites(), etapes, totaux,
                (int) totaux.stream().filter(b -> !b.suffisant()).count());
    }

    /** Agrege les besoins d'une liste de taches dans le cumul donne. */
    private static List<BesoinConsommable> besoins(List<Tache> surPlace, Map<Long, Cumul> cumul) {
        for (Tache tache : surPlace) {
            Consommable consommable = tache.getConsommable();
            if (consommable == null) {
                continue;
            }
            cumul.computeIfAbsent(consommable.getId(), cle -> new Cumul(consommable))
                    .ajouter(tache.getQuantitePrevue());
        }
        return cumul.values().stream().map(Cumul::figer).toList();
    }

    /**
     * Cumul d'un consommable sur une portion de tournee.
     *
     * <p><strong>{@code requis} reste {@code null} tant qu'aucune tache n'a
     * chiffre sa consommation.</strong> Le mettre a zero ferait afficher
     * « 0 kg de candi » la ou la verite est « il en faut, on ne sait pas
     * combien » — et une feuille de chargement qui annonce zero fait partir sans.
     */
    private static final class Cumul {

        private final Consommable consommable;
        private BigDecimal requis;

        private Cumul(Consommable consommable) {
            this.consommable = consommable;
        }

        private void ajouter(BigDecimal quantite) {
            if (quantite == null) {
                return;
            }
            requis = requis == null ? quantite : requis.add(quantite);
        }

        private BesoinConsommable figer() {
            BigDecimal stock = consommable.getQuantite();
            // Un besoin NON CHIFFRE ne declare jamais un manque : on ne peut pas
            // manquer d'une quantite qu'on n'a pas exprimee. Le declarer
            // insuffisant par prudence ferait clignoter la feuille entiere.
            boolean suffisant = requis == null || stock.compareTo(requis) >= 0;
            return new BesoinConsommable(consommable.getId(), consommable.getLibelle(),
                    consommable.getUnite(), requis, stock, suffisant);
        }
    }
}
