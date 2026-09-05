package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.web.dto.AnomalieReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Miroir du portage EWMA dans le navigateur (SPRINT-30, lot G).
 *
 * <p>Le mode local exécute la même détection d'anomalie <strong>sur
 * l'appareil</strong> (`frontend/src/local/ewma.ts`), ce qui ferme la ligne
 * « IA embarquée sur l'appareil » du §8. Deux implémentations d'une même
 * formule, dans deux langages : <strong>le risque n'est pas l'erreur, c'est la
 * dérive silencieuse</strong>. Un point signalé d'un côté et pas de l'autre ne
 * se verrait que sur un écran.
 *
 * <p>Ce test et {@code ewma.test.ts} fixent les <strong>mêmes nombres</strong>
 * sur la <strong>même série</strong>. Toucher l'un sans l'autre fait échouer une
 * des deux campagnes — c'est exactement le but, et c'est le seul dispositif qui
 * tienne quand le même calcul vit à deux endroits.
 */
class AnomalieEmbarqueeTest {

    /** La série d'{@code AnomalieServiceTest} : sept mesures stables, une pointe. */
    private static final double[] REFERENCE = {30.0, 30.2, 29.9, 30.1, 29.8, 30.3, 29.9, 50.0};

    private List<Mesure> serie(double... valeurs) {
        List<Mesure> liste = new ArrayList<>();
        Instant depart = Instant.parse("2026-09-01T08:00:00Z");
        for (int i = 0; i < valeurs.length; i++) {
            MesureId id = new MesureId(1L, TypeIndicateur.POIDS, depart.plus(i, ChronoUnit.DAYS));
            liste.add(new Mesure(id, BigDecimal.valueOf(valeurs[i])));
        }
        return liste;
    }

    @Test
    @DisplayName("rend exactement les nombres du portage navigateur")
    void memeSerieMemesNombres() {
        MesureRepository mesures = mock(MesureRepository.class);
        when(mesures.findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(1L, TypeIndicateur.POIDS))
                .thenReturn(serie(REFERENCE));
        // Sans moteur delegue : c'est le calcul LOCAL qu'on compare, celui-la
        // meme que le navigateur refait en mode local.
        AnomalieService service = new AnomalieService(mesures, new MoteurAnomalie() {
            @Override
            public boolean actif() {
                return false;
            }

            @Override
            public java.util.Optional<AnomalieReponse> scorer(Long rucheId, TypeIndicateur type,
                    List<PointSerie> points) {
                return java.util.Optional.empty();
            }
        });

        AnomalieReponse r = service.detecter(1L, TypeIndicateur.POIDS);

        // Les trois nombres du contrat. Ils figurent a l'identique dans
        // `frontend/src/local/ewma.test.ts`.
        assertThat(r.baseline()).isEqualTo(36.012);
        assertThat(r.ecartType()).isEqualTo(9.159);
        assertThat(r.anomalies()).singleElement()
                .satisfies(a -> {
                    assertThat(a.valeur()).isEqualByComparingTo(BigDecimal.valueOf(50.0));
                    assertThat(a.zScore()).isEqualTo(113.015);
                });
    }
}
