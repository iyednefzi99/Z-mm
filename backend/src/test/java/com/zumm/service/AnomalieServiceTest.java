package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.web.dto.AnomalieReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests unitaires de la détection d'anomalie EWMA (US-034, US-036). */
@ExtendWith(MockitoExtension.class)
class AnomalieServiceTest {

    /**
     * Moteur absent : le service doit alors calculer localement (EWMA).
     *
     * <p>Un simple double suffit depuis que la detection passe par un PORT
     * ({@code MoteurAnomalie}). Auparavant il fallait instancier le client HTTP du
     * microservice avec une URL vide pour le neutraliser — un test unitaire qui
     * construisait un client reseau pour ne surtout pas s'en servir.
     */
    private static final MoteurAnomalie MOTEUR_ABSENT = new MoteurAnomalie() {
        @Override
        public boolean actif() {
            return false;
        }

        @Override
        public java.util.Optional<com.zumm.web.dto.AnomalieReponse> scorer(
                Long rucheId, com.zumm.domain.TypeIndicateur type, java.util.List<PointSerie> serie) {
            return java.util.Optional.empty();
        }
    };

    @Mock
    private MesureRepository mesures;

    private AnomalieService service;

    private List<Mesure> serie(double... valeurs) {
        List<Mesure> liste = new ArrayList<>();
        Instant t = Instant.parse("2026-06-01T00:00:00Z");
        for (int i = 0; i < valeurs.length; i++) {
            MesureId id = new MesureId(1L, TypeIndicateur.POIDS, t.plusSeconds(i * 3600L));
            liste.add(new Mesure(id, BigDecimal.valueOf(valeurs[i])));
        }
        return liste;
    }

    @Test
    @DisplayName("repère une pointe isolée dans une série bruitée")
    void repereUnePointe() {
        service = new AnomalieService(mesures, MOTEUR_ABSENT);
        when(mesures.findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(1L, TypeIndicateur.POIDS))
                .thenReturn(serie(30.0, 30.2, 29.9, 30.1, 29.8, 30.3, 29.9, 50.0));

        AnomalieReponse r = service.detecter(1L, TypeIndicateur.POIDS);

        assertThat(r.nombrePoints()).isEqualTo(8);
        assertThat(r.baseline()).isNotNull();
        assertThat(r.anomalies())
                .anySatisfy(a -> assertThat(a.valeur()).isEqualByComparingTo(BigDecimal.valueOf(50.0)));
    }

    @Test
    @DisplayName("série stable : aucune anomalie")
    void serieStableSansAnomalie() {
        service = new AnomalieService(mesures, MOTEUR_ABSENT);
        when(mesures.findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(1L, TypeIndicateur.POIDS))
                .thenReturn(serie(30.0, 30.1, 29.9, 30.0, 30.1, 29.9));

        AnomalieReponse r = service.detecter(1L, TypeIndicateur.POIDS);

        assertThat(r.anomalies()).isEmpty();
    }

    @Test
    @DisplayName("série vide : pas de ligne de base, pas d'anomalie")
    void serieVide() {
        service = new AnomalieService(mesures, MOTEUR_ABSENT);
        when(mesures.findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(1L, TypeIndicateur.POIDS))
                .thenReturn(List.of());

        AnomalieReponse r = service.detecter(1L, TypeIndicateur.POIDS);

        assertThat(r.nombrePoints()).isZero();
        assertThat(r.baseline()).isNull();
        assertThat(r.anomalies()).isEmpty();
    }
}
