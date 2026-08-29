package com.zumm.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fournisseur meteo s'appuyant sur l'API publique Open-Meteo (US-029), sans cle.
 *
 * <p>Le timeout est court et toute erreur (reseau, format) renvoie
 * {@link Optional#empty()} : le service appelant retombe alors sur une estimation
 * hors-ligne. On ne fait donc jamais echouer une requete utilisateur pour un alea
 * reseau.
 *
 * <p>Conditions courantes et previsions journalieres partent dans la MEME requete
 * ({@code current=} et {@code daily=}) : l'API les sert ensemble, et le
 * {@code timezone=auto} garantit que les dates rendues sont celles du fuseau du
 * rucher — un jour de prevision cale sur UTC decalerait la lecture d'un
 * apiculteur en fin de journee.
 */
@Component
public class OpenMeteoFournisseur implements FournisseurMeteo {

    private static final Logger LOG = LoggerFactory.getLogger(OpenMeteoFournisseur.class);

    /** Plafond de l'API publique Open-Meteo pour {@code forecast_days}. */
    static final int JOURS_MAX = 16;

    private final RestClient client;

    public OpenMeteoFournisseur() {
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(2));
        fabrique.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder()
                .baseUrl("https://api.open-meteo.com/v1")
                .requestFactory(fabrique)
                .build();
    }

    @Override
    public Optional<Releve> releve(double latitude, double longitude, int joursPrevision) {
        int jours = Math.max(0, Math.min(joursPrevision, JOURS_MAX));
        try {
            ReponseOpenMeteo r = client.get()
                    .uri(uri -> {
                        uri.path("/forecast")
                                .queryParam("latitude", latitude)
                                .queryParam("longitude", longitude)
                                .queryParam("current",
                                        "temperature_2m,relative_humidity_2m,wind_speed_10m")
                                .queryParam("timezone", "auto");
                        if (jours > 0) {
                            uri.queryParam("daily", "temperature_2m_min,temperature_2m_max,"
                                            + "precipitation_sum,wind_speed_10m_max")
                                    .queryParam("forecast_days", jours);
                        }
                        return uri.build();
                    })
                    .retrieve()
                    .body(ReponseOpenMeteo.class);
            if (r == null || r.current() == null) {
                return Optional.empty();
            }
            Courant c = r.current();
            Meteo courante = new Meteo(c.temperature_2m(), c.relative_humidity_2m(),
                    c.wind_speed_10m());
            return Optional.of(new Releve(courante, jours(r.daily())));
        } catch (RuntimeException e) {
            LOG.debug("Open-Meteo indisponible ({}) : repli sur simulation.", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Open-Meteo rend le bloc journalier en colonnes paralleles, pas en lignes. On
     * les recoud en se calant sur {@code time}, et chaque autre colonne est lue
     * defensivement : une serie plus courte que les dates rend {@code null} pour la
     * mesure manquante plutot que de faire echouer tout le releve.
     */
    private List<Jour> jours(Quotidien daily) {
        if (daily == null || daily.time() == null) {
            return List.of();
        }
        List<Jour> resultat = new ArrayList<>(daily.time().size());
        for (int i = 0; i < daily.time().size(); i++) {
            LocalDate date = date(daily.time().get(i));
            if (date == null) {
                continue;
            }
            resultat.add(new Jour(date,
                    valeur(daily.temperature_2m_min(), i),
                    valeur(daily.temperature_2m_max(), i),
                    valeur(daily.precipitation_sum(), i),
                    valeur(daily.wind_speed_10m_max(), i)));
        }
        return List.copyOf(resultat);
    }

    private LocalDate date(String iso) {
        try {
            return iso == null ? null : LocalDate.parse(iso);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Double valeur(List<Double> colonne, int index) {
        return colonne == null || index >= colonne.size() ? null : colonne.get(index);
    }

    // Projections du JSON Open-Meteo (champs nommes comme l'API).
    private record ReponseOpenMeteo(Courant current, Quotidien daily) {
    }

    private record Courant(double temperature_2m, Integer relative_humidity_2m, Double wind_speed_10m) {
    }

    private record Quotidien(
            List<String> time,
            List<Double> temperature_2m_min,
            List<Double> temperature_2m_max,
            List<Double> precipitation_sum,
            List<Double> wind_speed_10m_max) {
    }
}
