package com.zumm.service;

import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.AlerteRepository;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.SyntheseReponse;
import com.zumm.web.dto.SyntheseReponse.Roi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synthese de pilotage et ROI (US-015).
 *
 * <p>Tout est agrege EN BASE depuis le SPRINT-18. L'implementation precedente
 * portait le meme defaut que le tableau de bord avant sa correction : elle
 * chargeait TOUTES les mesures de poids du tenant pour n'en retenir qu'une valeur
 * par ruche, et parcourait toutes les visites pour en compter les motifs. Sur un
 * parc reel, la synthese — c'est-a-dire l'ecran d'accueil du responsable — etait
 * donc la page la plus couteuse de l'application.
 *
 * <p>Le ROI repose sur une economie de reference <em>indicative</em> (prix du miel,
 * cout d'une visite), destinee a la demonstration : ces constantes rejoindront
 * {@code ConfigZumm.ini} lorsque le module production/recolte sera livre.
 */
@Service
@Transactional(readOnly = true)
public class SyntheseService {

    /** Valorisation indicative du kg de miel produit (EUR). */
    private static final BigDecimal PRIX_MIEL_KG_EUR = BigDecimal.valueOf(12);
    /** Cout indicatif d'une intervention/visite (EUR). */
    private static final BigDecimal COUT_VISITE_EUR = BigDecimal.valueOf(25);

    private final VisiteRepository visites;
    private final MesureRepository mesures;
    private final RucheRepository ruches;
    private final AlerteRepository alertes;

    public SyntheseService(VisiteRepository visites, MesureRepository mesures,
            RucheRepository ruches, AlerteRepository alertes) {
        this.visites = visites;
        this.mesures = mesures;
        this.ruches = ruches;
        this.alertes = alertes;
    }

    public SyntheseReponse synthese() {
        long nombreRuches = ruches.count();
        long nombreVisites = visites.count();

        // LinkedHashMap : l'ordre est conserve, donc la restitution est stable d'un
        // appel a l'autre. Une carte non ordonnee ferait bouger l'affichage sans
        // qu'aucune donnee n'ait change.
        Map<String, Long> parRaison = new LinkedHashMap<>();
        for (VisiteRepository.CompteParRaison ligne : visites.compterParRaison()) {
            parRaison.put(ligne.getRaison().enBase(), ligne.getNombre());
        }

        Double moyenne = visites.productiviteMoyenneGlobale();
        Double productiviteMoyenne = moyenne == null ? null
                : BigDecimal.valueOf(moyenne).setScale(2, RoundingMode.HALF_UP).doubleValue();

        BigDecimal poidsTotal = poidsTotalActuel();
        long alertesOuvertes = alertes.countByOuverteTrue();

        return new SyntheseReponse(nombreRuches, nombreVisites, parRaison, productiviteMoyenne,
                poidsTotal, alertesOuvertes, roi(poidsTotal, nombreVisites));
    }

    /**
     * Somme des derniers poids connus par ruche (proxy de production).
     *
     * <p>S'appuie sur l'agregat deja calcule en base pour le tableau de bord : une
     * ligne par ruche, la derniere valeur obtenue par {@code last(valeur, instant)}
     * de TimescaleDB. La somme finale porte donc sur quelques centaines de lignes
     * au plus, la ou l'implementation precedente en rapatriait des millions.
     */
    private BigDecimal poidsTotalActuel() {
        return mesures.agregatPoids(TypeIndicateur.POIDS.enBase()).stream()
                .map(MesureRepository.AgregatPoids::getActuel)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Roi roi(BigDecimal poidsTotal, long nombreVisites) {
        BigDecimal valeur = poidsTotal.multiply(PRIX_MIEL_KG_EUR).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cout = COUT_VISITE_EUR.multiply(BigDecimal.valueOf(nombreVisites))
                .setScale(2, RoundingMode.HALF_UP);
        Double pourcent = cout.signum() == 0 ? null
                : valeur.subtract(cout)
                        .divide(cout, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();
        return new Roi(valeur, cout, pourcent);
    }
}
