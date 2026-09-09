package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.Site;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.ZoneTraiteeRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.ExpositionRucher;
import com.zumm.web.dto.ZoneTraiteeCorps;
import com.zumm.web.dto.ZoneTraiteeReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposition aux zones traitees, sur declaration (SPRINT-33, lot K).
 *
 * <p>Ferme la ligne « evaluation de l'exposition aux zones traitees » (§2),
 * restee 🟡 depuis le SPRINT-32. Le motif d'alors etait exact et n'a pas change :
 * <strong>aucune couche ouverte ne dit ce qui a ete epandu ni quand</strong>, et
 * {@code distanceCultureM} mesure la distance a une culture, pas a un
 * traitement.
 *
 * <p>Ce qui change est la question posee. Plutot que d'attendre une source
 * publique qui n'existe pas, on accueille celle qui existe : l'apiculteur a qui
 * le voisin annonce un traitement, qui voit passer un pulverisateur, qui lit un
 * avis affiche. C'est litteralement la decision D1 de l'ADR-015 appliquee a une
 * seconde couche — <em>la donnee est accueillie, jamais interrogee</em>.
 *
 * <p><strong>Trois bornes, et elles font la difference entre une fonction et une
 * pretention.</strong>
 *
 * <ol>
 *   <li><strong>Aucune identite de tiers.</strong> Le §13 refuse un annuaire de
 *       voisins ; une zone traitee est un polygone, une date et une substance
 *       quand on la connait. Le modele ne porte pas de « qui ».
 *   <li><strong>Le silence n'est pas une garantie.</strong> La reponse porte le
 *       nombre de declarations et la date de la plus recente : « aucune zone a
 *       proximite » se lit « rien ne m'a ete declare », jamais « rien n'a ete
 *       epandu ».
 *   <li><strong>Aucun indice d'exposition n'est calcule.</strong> Agreger une
 *       distance, une surface et une substance inconnue en une note de 0 a 100
 *       produirait un chiffre invérifiable — le meme refus qu'au SPRINT-23 pour
 *       la note globale de comparaison d'emplacements, et qu'au SPRINT-32 pour
 *       la correlation sante × flore.
 * </ol>
 */
@Service
public class ZoneTraiteeService {

    private final ZoneTraiteeRepository zones;
    private final SiteRepository sites;
    private final ConfigurationMetier configuration;

    public ZoneTraiteeService(ZoneTraiteeRepository zones, SiteRepository sites,
            ConfigurationMetier configuration) {
        this.zones = zones;
        this.sites = sites;
        this.configuration = configuration;
    }

    /**
     * Declare une zone traitee.
     *
     * <p>La geometrie est refusee si PostGIS ne sait pas la lire : mieux vaut un
     * 400 a la saisie qu'une couche qui fait echouer un calcul six mois plus
     * tard.
     */
    @Transactional
    public ZoneTraiteeReponse declarer(ZoneTraiteeCorps corps) {
        if (corps.geometrie().isNull() || corps.geometrie().isMissingNode()) {
            throw new RequeteInvalide("Une zone sans geometrie ne designe aucun terrain.");
        }
        // Une date future serait une PREVISION de traitement. Le champ dit ce
        // qui a ete fait ; accepter demain melangerait constat et annonce dans
        // la meme colonne, et la lecture « sous delai de rentree » n'aurait plus
        // de sens.
        if (corps.dateTraitement().isAfter(LocalDate.now())) {
            throw new RequeteInvalide(
                    "La date de traitement est dans le futur : ce champ porte ce qui a ete "
                            + "fait, pas ce qui est annonce.");
        }
        Long id = zones.inserer(corps.geometrie().toString(), corps.dateTraitement(),
                normaliser(corps.substance()), corps.origine(), corps.delaiRentreeH(),
                normaliser(corps.note()));
        return zones.lister().stream()
                .filter(z -> z.id().equals(id))
                .findFirst()
                .orElseThrow(() -> RessourceIntrouvable.de("ZoneTraitee", id));
    }

    @Transactional(readOnly = true)
    public List<ZoneTraiteeReponse> lister() {
        return zones.lister();
    }

    @Transactional
    public void supprimer(Long id) {
        if (zones.supprimer(id) == 0) {
            throw RessourceIntrouvable.de("ZoneTraitee", id);
        }
    }

    /**
     * Ce qui a ete declare autour d'un rucher.
     *
     * <p>Le compte des zones <strong>encore sous delai de rentree</strong> est la
     * seule information du document qui appelle une decision le jour meme. Une
     * zone sans delai renseigne n'y entre pas : {@code null} n'est pas zero, et
     * compter l'inconnu comme « ecoule » serait rassurant a tort.
     */
    @Transactional(readOnly = true)
    public ExpositionRucher autour(Long siteId, LocalDate jour) {
        Site site = sites.findById(siteId)
                .orElseThrow(() -> RessourceIntrouvable.de("Site", siteId));
        BigDecimal rayonKm = site.getRayonButinageKm() != null
                ? site.getRayonButinageKm()
                : BigDecimal.valueOf(configuration.seuils().rayonButinageKm());

        List<ZoneTraiteeReponse> proches =
                zones.autour(siteId, rayonKm.doubleValue() * 1000);
        LocalDate derniere = proches.stream()
                .map(ZoneTraiteeReponse::dateTraitement)
                .max(LocalDate::compareTo)
                .orElse(null);
        int sousDelai = (int) proches.stream().filter(z -> sousDelaiRentree(z, jour)).count();

        return new ExpositionRucher(site.getId(), site.getNom(), rayonKm,
                proches.size(), derniere, zones.distanceMin(siteId), sousDelai, proches);
    }

    /**
     * Le delai de rentree est-il encore en cours ?
     *
     * <p>Compte en JOURS pleins a partir du lendemain du traitement, ce qui est
     * la lecture prudente : un delai de 24 h annonce le mardi couvre le mercredi
     * entier, faute de connaitre l'heure de l'epandage. Reclamer cette heure
     * ferait echouer la saisie la plus courante — « ils ont traite hier ».
     */
    static boolean sousDelaiRentree(ZoneTraiteeReponse zone, LocalDate jour) {
        if (zone.delaiRentreeH() == null) {
            return false;
        }
        long jours = (zone.delaiRentreeH() + 23L) / 24L;
        return !jour.isAfter(zone.dateTraitement().plusDays(jours));
    }

    private static String normaliser(String valeur) {
        if (valeur == null) {
            return null;
        }
        String propre = valeur.trim();
        return propre.isEmpty() ? null : propre;
    }
}
