package com.zumm.service;

import com.zumm.domain.Mesure;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.dto.PrevisionRecolte;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prévision de récolte (US-042, SPRINT-09) : extrapolation de la tendance du poids.
 *
 * <p>Pour chaque ruche, une régression linéaire des moindres carrés sur la série de
 * poids donne le gain journalier moyen (pente). On en déduit une projection à 7
 * jours et un signal de tendance (hausse / stable / baisse). Le calcul est fait en
 * mémoire — volumes modestes à ce stade, comme le tableau de bord production.
 */
@Service
@Transactional(readOnly = true)
public class PrevisionRecolteService {

    /** Horizon de projection, en jours. */
    private static final int HORIZON_JOURS = 7;
    /** Seuil (kg/jour) au-delà duquel la tendance n'est plus considérée « stable ». */
    private static final double SEUIL_TENDANCE = 0.10;

    private final MesureRepository mesures;
    private final RucheRepository ruches;

    public PrevisionRecolteService(MesureRepository mesures, RucheRepository ruches) {
        this.mesures = mesures;
        this.ruches = ruches;
    }

    /** Prévision de récolte pour chaque ruche du tenant. */
    public List<PrevisionRecolte> previsions() {
        Map<Long, List<Mesure>> parRuche = new LinkedHashMap<>();
        for (Mesure m : mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS)) {
            parRuche.computeIfAbsent(m.getId().getRucheId(), cle -> new ArrayList<>()).add(m);
        }

        List<PrevisionRecolte> resultat = new ArrayList<>();
        for (Ruche ruche : ruches.findAll()) {
            resultat.add(prevoir(ruche, parRuche.getOrDefault(ruche.getId(), List.of())));
        }
        return resultat;
    }

    private PrevisionRecolte prevoir(Ruche ruche, List<Mesure> serie) {
        if (serie.isEmpty()) {
            return new PrevisionRecolte(ruche.getId(), ruche.getModele(), null, null, null,
                    PrevisionRecolte.INCONNUE, 0);
        }
        BigDecimal actuel = serie.get(serie.size() - 1).getValeur();
        if (serie.size() < 2) {
            return new PrevisionRecolte(ruche.getId(), ruche.getModele(), arrondi(actuel), null, null,
                    PrevisionRecolte.INCONNUE, serie.size());
        }

        double penteKgParJour = regressionPente(serie);
        BigDecimal projection = actuel.add(BigDecimal.valueOf(penteKgParJour * HORIZON_JOURS));
        return new PrevisionRecolte(ruche.getId(), ruche.getModele(), arrondi(actuel),
                arrondi(BigDecimal.valueOf(penteKgParJour)), arrondi(projection),
                qualifier(penteKgParJour), serie.size());
    }

    /**
     * Pente (kg/jour) de la régression linéaire des moindres carrés du poids en
     * fonction du temps, l'origine des abscisses étant la première mesure.
     */
    private double regressionPente(List<Mesure> serie) {
        Instant origine = serie.get(0).getId().getInstant();
        int n = serie.size();
        double sommeX = 0;
        double sommeY = 0;
        double sommeXY = 0;
        double sommeXX = 0;
        for (Mesure m : serie) {
            double x = joursDepuis(origine, m.getId().getInstant());
            double y = m.getValeur().doubleValue();
            sommeX += x;
            sommeY += y;
            sommeXY += x * y;
            sommeXX += x * x;
        }
        double denominateur = n * sommeXX - sommeX * sommeX;
        if (denominateur == 0) {
            // Toutes les mesures au même instant : pente indéfinie → considérée nulle.
            return 0;
        }
        return (n * sommeXY - sommeX * sommeY) / denominateur;
    }

    private static double joursDepuis(Instant origine, Instant instant) {
        return Duration.between(origine, instant).toSeconds() / 86_400.0;
    }

    private static String qualifier(double penteKgParJour) {
        if (penteKgParJour > SEUIL_TENDANCE) {
            return PrevisionRecolte.HAUSSE;
        }
        if (penteKgParJour < -SEUIL_TENDANCE) {
            return PrevisionRecolte.BAISSE;
        }
        return PrevisionRecolte.STABLE;
    }

    private static BigDecimal arrondi(BigDecimal valeur) {
        return valeur == null ? null : valeur.setScale(2, RoundingMode.HALF_UP);
    }
}
