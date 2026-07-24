package com.zumm.service;

import com.zumm.domain.Mesure;
import com.zumm.domain.TypeIndicateur;
import com.zumm.web.dto.AnomalieReponse;
import com.zumm.web.dto.AnomalieReponse.PointAnomalie;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client du microservice IA Python (US-035), activé par {@code zumm.ia.url}.
 *
 * <p>Découplage REST/JSON : le back-end délègue la détection d'anomalie au service
 * Python quand il est configuré, et retombe silencieusement sur la détection EWMA
 * locale ({@link AnomalieService}) sinon ou en cas d'indisponibilité — jamais
 * d'échec de requête pour un aléa réseau, comme pour le contexte météo.
 */
@Component
public class ClientAnomalieIA {

    private static final Logger LOG = LoggerFactory.getLogger(ClientAnomalieIA.class);

    private final String url;
    private final RestClient client;

    public ClientAnomalieIA(@Value("${zumm.ia.url:}") String url) {
        this.url = url;
        SimpleClientHttpRequestFactory fabrique = new SimpleClientHttpRequestFactory();
        fabrique.setConnectTimeout(Duration.ofSeconds(2));
        fabrique.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().requestFactory(fabrique).build();
    }

    /** Vrai si un microservice IA est configuré. */
    public boolean actif() {
        return url != null && !url.isBlank();
    }

    /** Score la série via le microservice ; {@link Optional#empty()} si indisponible. */
    public Optional<AnomalieReponse> scorer(Long rucheId, TypeIndicateur type, List<Mesure> serie) {
        if (!actif()) {
            return Optional.empty();
        }
        try {
            List<Point> points = serie.stream()
                    .map(m -> new Point(m.getId().getInstant().toString(), m.getValeur()))
                    .toList();
            ReponseIa r = client.post()
                    .uri(url + "/score")
                    .body(new RequeteIa(points))
                    .retrieve()
                    .body(ReponseIa.class);
            if (r == null) {
                return Optional.empty();
            }
            return Optional.of(versReponse(rucheId, type, r));
        } catch (RuntimeException e) {
            LOG.debug("Microservice IA indisponible ({}) : repli sur EWMA local.", e.getMessage());
            return Optional.empty();
        }
    }

    private AnomalieReponse versReponse(Long rucheId, TypeIndicateur type, ReponseIa r) {
        List<PointAnomalie> anomalies = r.anomalies() == null ? List.of()
                : r.anomalies().stream()
                        .map(a -> new PointAnomalie(Instant.parse(a.instant()), a.valeur(), a.zScore()))
                        .toList();
        return new AnomalieReponse(rucheId, type, r.alpha(), r.seuilZ(),
                r.baseline(), r.ecartType(), r.nombrePoints(), anomalies);
    }

    // Contrats JSON échangés avec le microservice.
    private record RequeteIa(List<Point> series) {
    }

    private record Point(String instant, BigDecimal valeur) {
    }

    private record ReponseIa(double alpha, double seuilZ, Double baseline, Double ecartType,
            int nombrePoints, List<PointIa> anomalies) {
    }

    private record PointIa(String instant, BigDecimal valeur, double zScore) {
    }
}
