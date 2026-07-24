package com.zumm.service;

import com.zumm.domain.Mesure;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.web.dto.AnomalieReponse;
import com.zumm.web.dto.AnomalieReponse.PointAnomalie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detection d'anomalie adaptative par moyenne mobile exponentielle EWMA (US-034).
 *
 * <p>Pour chaque ruche et indicateur, on maintient une ligne de base (moyenne EWMA)
 * et une variance EWMA incrementale (formule de Finch). Le z-score d'une mesure est
 * son ecart a la ligne de base rapporte a l'ecart-type ; au-dela du seuil, la mesure
 * est signalee comme anomalie. La ligne de base s'adapte a la derive lente (saison,
 * croissance de la colonie) tout en detectant les ruptures brutales.
 *
 * <p>Approche statistique legere et sans dependance : le microservice IA Python
 * (US-035) prendra le relais pour des modeles plus riches.
 */
@Service
@Transactional(readOnly = true)
public class AnomalieService {

    /** Poids de la derniere mesure dans la moyenne EWMA (reactivite). */
    private static final double ALPHA = 0.3;
    /** Seuil de z-score au-dela duquel un point est une anomalie. */
    private static final double SEUIL_Z = 3.0;

    private final MesureRepository mesures;

    public AnomalieService(MesureRepository mesures) {
        this.mesures = mesures;
    }

    public AnomalieReponse detecter(Long rucheId, TypeIndicateur type) {
        List<Mesure> serie = mesures.findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(rucheId, type);
        List<PointAnomalie> anomalies = new ArrayList<>();

        if (serie.isEmpty()) {
            return new AnomalieReponse(rucheId, type, ALPHA, SEUIL_Z, null, null, 0, anomalies);
        }

        double moyenne = serie.get(0).getValeur().doubleValue();
        double variance = 0.0;
        for (int i = 1; i < serie.size(); i++) {
            double x = serie.get(i).getValeur().doubleValue();
            double ecart = x - moyenne;
            double increment = ALPHA * ecart;
            // z-score AVANT mise a jour : ecart a la ligne de base connue.
            double ecartType = Math.sqrt(variance);
            if (ecartType > 0) {
                double z = ecart / ecartType;
                if (Math.abs(z) > SEUIL_Z) {
                    anomalies.add(new PointAnomalie(
                            serie.get(i).getId().getInstant(),
                            serie.get(i).getValeur(),
                            arrondi(z)));
                }
            }
            // Mise a jour EWMA de la moyenne puis de la variance (Finch, incremental).
            moyenne += increment;
            variance = (1 - ALPHA) * (variance + ecart * increment);
        }

        return new AnomalieReponse(rucheId, type, ALPHA, SEUIL_Z,
                arrondi(moyenne), arrondi(Math.sqrt(variance)), serie.size(), anomalies);
    }

    private static double arrondi(double valeur) {
        return Math.round(valeur * 1000.0) / 1000.0;
    }
}
