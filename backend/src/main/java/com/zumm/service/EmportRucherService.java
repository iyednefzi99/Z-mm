package com.zumm.service;

import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.EmportRucher;
import com.zumm.web.dto.RucheReponse;
import com.zumm.web.dto.TacheReponse;
import com.zumm.web.dto.TraitementReponse;
import com.zumm.web.dto.VisiteReponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emport d'un rucher pour la consultation hors ligne (SPRINT-24, lot C).
 *
 * <p>Ferme la ligne « consultation hors ligne des données » du §11 et le
 * contournement « ouvrez les fiches de vos ruchers avant d'entrer en zone
 * blanche » du §13, que trois éditeurs concurrents conseillent à leurs propres
 * utilisateurs. Un contournement enseigné par trois éditeurs n'est pas une bonne
 * pratique : c'est une fonction manquante — et le fait qu'il soit manuel chez
 * eux dit quelle forme elle doit prendre ici.
 *
 * <p><strong>Un seul appel, et c'est le point.</strong> Six requêtes assemblées
 * côté serveur plutôt que six appels que le navigateur passerait un par un : sur
 * un réseau qui s'effondre — c'est le contexte même de la fonction — un emport à
 * moitié fait serait pire que pas d'emport du tout, parce qu'il aurait l'air
 * complet.
 *
 * <p><strong>La position sort masquée.</strong> {@code site} passe par
 * {@link SiteService}, donc par {@code PolitiquePositions}. Emporter un rucher
 * ne doit pas devenir le moyen d'obtenir en clair, sur un appareil sans session,
 * ce que l'API dégrade en ligne — c'est l'endroit exact où un contournement de
 * la politique aurait pu se glisser sans être vu.
 *
 * <p>Ce que l'instantané laisse dehors est documenté sur {@link EmportRucher} :
 * mesures, positions exactes, météo.
 */
@Service
@Transactional(readOnly = true)
public class EmportRucherService {

    /**
     * Fenêtre des tâches emportées.
     *
     * <p>Ce qui est dû dans le mois : au-delà, on n'emporte plus un rucher, on
     * emporte un backlog — et il ne se lit pas debout entre deux ruches.
     */
    private static final int HORIZON_TACHES_JOURS = 30;

    private final SiteService sites;
    private final RucheRepository ruches;
    private final VisiteRepository visites;
    private final TacheRepository taches;
    private final TraitementRepository traitements;

    public EmportRucherService(SiteService sites, RucheRepository ruches,
            VisiteRepository visites, TacheRepository taches, TraitementRepository traitements) {
        this.sites = sites;
        this.ruches = ruches;
        this.visites = visites;
        this.taches = taches;
        this.traitements = traitements;
    }

    public EmportRucher pourSite(Long siteId) {
        // Lève 404 si le rucher n'existe pas dans ce tenant, AVANT de rien lire
        // d'autre : un instantané vide serait indiscernable d'un rucher vide.
        var site = sites.obtenir(siteId);
        LocalDate jour = LocalDate.now();

        List<Ruche> duSite = ruches.findBySite_IdOrderByIdAsc(siteId);
        Set<Long> rucheIds = duSite.stream().map(Ruche::getId).collect(Collectors.toSet());

        // Les ruches CLÔTURÉES restent de l'emport : au rucher, la ruche morte
        // est encore là physiquement, et savoir qu'elle est enregistrée comme
        // clôturée évite de la ressaisir.
        List<RucheReponse> vueRuches = duSite.stream().map(RucheReponse::de).toList();

        List<VisiteReponse> dernieres = visites.dernieresVisitesParRuche().stream()
                .filter(v -> rucheIds.contains(v.getRuche().getId()))
                .sorted((a, b) -> Long.compare(a.getRuche().getId(), b.getRuche().getId()))
                // Sans photo ni pathologie : l'emport doit rester petit, et une
                // URL de photo inaccessible hors ligne n'aide personne.
                .map(this::sansPieces)
                .toList();

        List<TacheReponse> aFaire = taches.findByFaiteFalseOrderByPrioriteAscEcheanceAsc().stream()
                .filter(t -> t.getRuche() != null && rucheIds.contains(t.getRuche().getId()))
                .filter(t -> t.getEcheance() == null
                        || !t.getEcheance().isAfter(jour.plusDays(HORIZON_TACHES_JOURS)))
                .map(TacheReponse::de)
                .toList();

        // Les carences en cours sont la seule donnée de l'emport qui INTERDIT un
        // geste : récolter une ruche sous carence est ce que le lot A refuse en
        // 409. Hors ligne, le refus ne peut pas venir du serveur — il faut donc
        // que l'information soit sur l'appareil, sans quoi la règle disparaît
        // exactement là où elle sert.
        List<TraitementReponse> carences = traitements.sousCarenceAu(jour).stream()
                .filter(t -> rucheIds.contains(t.getRuche().getId()))
                .filter(t -> t.sousCarence(jour))
                .map(t -> TraitementReponse.de(t, jour))
                .toList();

        return new EmportRucher(Instant.now(), site, vueRuches, dernieres, aFaire, carences);
    }

    private VisiteReponse sansPieces(Visite visite) {
        return VisiteReponse.de(visite, List.of(), List.of());
    }
}
