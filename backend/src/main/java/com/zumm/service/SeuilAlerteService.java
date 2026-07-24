package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.domain.Alerte;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.AlerteRepository;
import com.zumm.web.dto.AlerteReponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logique d'alerte a seuils avec hysteresis / anti-rebond (US-018).
 *
 * <p>A chaque mesure ingeree (US-017), on compare la valeur aux seuils de
 * {@code ConfigZumm.ini}. Pour eviter le battement (« flapping ») quand la valeur
 * oscille autour du seuil, on definit une bande d'hysteresis :
 *
 * <ul>
 *   <li>zone d'alerte : au-dela du seuil — ouvre une alerte si aucune n'est ouverte ;</li>
 *   <li>zone sure : revenue en deca du seuil d'au moins la marge — ferme l'alerte ;</li>
 *   <li>zone neutre : entre les deux — aucun changement d'etat.</li>
 * </ul>
 *
 * <p>Une seule alerte ouverte par (ruche, indicateur) — garantie applicative doublee
 * par un index partiel unique en base (migration V8).
 */
@Service
@Transactional
public class SeuilAlerteService {

    /** Largeur de la bande d'hysteresis, en fraction du seuil (anti-rebond). */
    private static final double FRACTION_HYSTERESIS = 0.05;

    private enum Zone { ALERTE, NEUTRE, SURE }

    private final AlerteRepository alertes;
    private final ConfigurationMetier configuration;

    public SeuilAlerteService(AlerteRepository alertes, ConfigurationMetier configuration) {
        this.alertes = alertes;
        this.configuration = configuration;
    }

    /**
     * Evalue une mesure et met a jour l'etat d'alerte de la ruche pour cet
     * indicateur. Renvoie les alertes ouvertes ou fermees par cette mesure (vide si
     * l'etat n'a pas change).
     */
    public List<AlerteReponse> evaluer(Ruche ruche, TypeIndicateur type, BigDecimal valeur) {
        Depassement d = analyser(type, valeur, configuration.seuils());
        Optional<Alerte> ouverte = alertes.findByRuche_IdAndTypeIndicateurAndOuverteTrue(ruche.getId(), type);

        return switch (d.zone()) {
            case ALERTE -> ouverte.isPresent() ? List.of()
                    : List.of(AlerteReponse.de(alertes.save(
                            new Alerte(ruche, type, d.niveau(), d.message(), valeur))));
            case SURE -> ouverte.map(a -> {
                a.fermer();
                return List.of(AlerteReponse.de(a));
            }).orElseGet(List::of);
            case NEUTRE -> List.of();
        };
    }

    private record Depassement(Zone zone, String niveau, String message) {
        static Depassement neutre() {
            return new Depassement(Zone.NEUTRE, null, null);
        }
    }

    private Depassement analyser(TypeIndicateur type, BigDecimal mesure, SeuilsMetier s) {
        double v = mesure.doubleValue();
        return switch (type) {
            case POIDS -> bas(v, s.poidsRucheAlerteKg(), Alerte.CRITIQUE,
                    "Poids %.1f kg sous le seuil de %d kg".formatted(v, s.poidsRucheAlerteKg()));
            case HUMIDITE -> haut(v, s.humiditeMaxPourcent(), Alerte.ATTENTION,
                    "Humidité %.0f%% au-dessus du seuil de %d%%".formatted(v, s.humiditeMaxPourcent()));
            case TEMPERATURE -> temperature(v, s);
            // Aucun seuil parametre pour l'activite : jamais d'alerte.
            case ACTIVITE -> Depassement.neutre();
        };
    }

    /** Seuil bas (alerte si la valeur passe SOUS le seuil) : poids. */
    private Depassement bas(double v, int seuil, String niveau, String message) {
        double marge = seuil * FRACTION_HYSTERESIS;
        if (v < seuil) {
            return new Depassement(Zone.ALERTE, niveau, message);
        }
        if (v >= seuil + marge) {
            return new Depassement(Zone.SURE, niveau, message);
        }
        return Depassement.neutre();
    }

    /** Seuil haut (alerte si la valeur passe AU-DESSUS du seuil) : humidite. */
    private Depassement haut(double v, int seuil, String niveau, String message) {
        double marge = seuil * FRACTION_HYSTERESIS;
        if (v > seuil) {
            return new Depassement(Zone.ALERTE, niveau, message);
        }
        if (v <= seuil - marge) {
            return new Depassement(Zone.SURE, niveau, message);
        }
        return Depassement.neutre();
    }

    /** Double seuil (bas ET haut) pour la temperature. */
    private Depassement temperature(double v, SeuilsMetier s) {
        int min = s.temperatureMinCelsius();
        int max = s.temperatureMaxCelsius();
        double margeMin = min * FRACTION_HYSTERESIS;
        double margeMax = max * FRACTION_HYSTERESIS;
        if (v < min) {
            return new Depassement(Zone.ALERTE, Alerte.ATTENTION,
                    "Température %.1f°C sous le seuil de %d°C".formatted(v, min));
        }
        if (v > max) {
            return new Depassement(Zone.ALERTE, Alerte.ATTENTION,
                    "Température %.1f°C au-dessus du seuil de %d°C".formatted(v, max));
        }
        if (v >= min + margeMin && v <= max - margeMax) {
            return new Depassement(Zone.SURE, Alerte.ATTENTION, null);
        }
        return Depassement.neutre();
    }
}
