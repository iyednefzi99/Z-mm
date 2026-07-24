package com.zumm.service;

import java.time.Duration;
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
 */
@Component
public class OpenMeteoFournisseur implements FournisseurMeteo {

    private static final Logger LOG = LoggerFactory.getLogger(OpenMeteoFournisseur.class);

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
    public Optional<Meteo> courante(double latitude, double longitude) {
        try {
            ReponseOpenMeteo r = client.get()
                    .uri(uri -> uri.path("/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,relative_humidity_2m,wind_speed_10m")
                            .build())
                    .retrieve()
                    .body(ReponseOpenMeteo.class);
            if (r == null || r.current() == null) {
                return Optional.empty();
            }
            Courant c = r.current();
            return Optional.of(new Meteo(c.temperature_2m(), c.relative_humidity_2m(), c.wind_speed_10m()));
        } catch (RuntimeException e) {
            LOG.debug("Open-Meteo indisponible ({}) : repli sur simulation.", e.getMessage());
            return Optional.empty();
        }
    }

    // Projections du JSON Open-Meteo (champs nommes comme l'API).
    private record ReponseOpenMeteo(Courant current) {
    }

    private record Courant(double temperature_2m, Integer relative_humidity_2m, Double wind_speed_10m) {
    }
}
