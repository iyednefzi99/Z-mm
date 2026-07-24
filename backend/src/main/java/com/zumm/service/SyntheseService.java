package com.zumm.service;

import com.zumm.domain.Mesure;
import com.zumm.domain.TypeIndicateur;
import com.zumm.domain.Visite;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Synthese de pilotage et ROI (US-015).
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

        Map<String, Long> parRaison = new LinkedHashMap<>();
        long nombreVisites = 0;
        double cumulProd = 0;
        long compteProd = 0;
        for (Visite v : visites.findAll()) {
            nombreVisites++;
            parRaison.merge(v.getRaison().enBase(), 1L, Long::sum);
            if (v.getProductivite() != null) {
                cumulProd += v.getProductivite();
                compteProd++;
            }
        }
        Double productiviteMoyenne = compteProd == 0 ? null
                : BigDecimal.valueOf(cumulProd / compteProd).setScale(2, RoundingMode.HALF_UP).doubleValue();

        BigDecimal poidsTotal = poidsTotalActuel();
        long alertesOuvertes = alertes.findByOuverteTrueOrderByOuverteLeDesc().size();

        return new SyntheseReponse(nombreRuches, nombreVisites, parRaison, productiviteMoyenne,
                poidsTotal, alertesOuvertes, roi(poidsTotal, nombreVisites));
    }

    /** Somme des derniers poids connus par ruche (proxy de production). */
    private BigDecimal poidsTotalActuel() {
        Map<Long, BigDecimal> dernierParRuche = new LinkedHashMap<>();
        // Trie par ruche puis instant croissant : la derniere valeur vue est la plus recente.
        for (Mesure m : mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS)) {
            dernierParRuche.put(m.getId().getRucheId(), m.getValeur());
        }
        return dernierParRuche.values().stream()
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
