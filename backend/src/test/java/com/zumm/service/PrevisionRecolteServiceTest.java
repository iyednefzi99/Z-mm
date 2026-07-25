package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zumm.domain.EtatRuche;
import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.dto.PrevisionRecolte;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Tests unitaires de la prévision de récolte (US-042, SPRINT-09). */
@ExtendWith(MockitoExtension.class)
class PrevisionRecolteServiceTest {

    @Mock
    private MesureRepository mesures;

    @Mock
    private RucheRepository ruches;

    private PrevisionRecolteService service;
    private Ruche ruche;

    @BeforeEach
    void init() {
        service = new PrevisionRecolteService(mesures, ruches);
        ruche = new Ruche("Langstroth", null, null, EtatRuche.ACTIVE);
        ReflectionTestUtils.setField(ruche, "id", 1L);
        when(ruches.findAll()).thenReturn(List.of(ruche));
    }

    /** Série d'un point par jour, à partir de {@code base}, avec un gain journalier fixe. */
    private List<Mesure> serieJournaliere(double base, double gainParJour, int jours) {
        List<Mesure> liste = new ArrayList<>();
        Instant t0 = Instant.parse("2026-06-01T00:00:00Z");
        for (int i = 0; i < jours; i++) {
            MesureId id = new MesureId(1L, TypeIndicateur.POIDS, t0.plusSeconds(i * 86_400L));
            liste.add(new Mesure(id, BigDecimal.valueOf(base + i * gainParJour)));
        }
        return liste;
    }

    @Test
    @DisplayName("série en hausse régulière : pente ≈ gain/jour, projection à 7 j, tendance HAUSSE")
    void tendanceHausse() {
        when(mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS))
                .thenReturn(serieJournaliere(30.0, 1.5, 6)); // 30 → 37.5 kg

        PrevisionRecolte p = service.previsions().get(0);

        assertThat(p.tendance()).isEqualTo(PrevisionRecolte.HAUSSE);
        assertThat(p.tendanceKgParJour()).isEqualByComparingTo(BigDecimal.valueOf(1.5));
        assertThat(p.poidsActuelKg()).isEqualByComparingTo(BigDecimal.valueOf(37.5));
        // 37.5 + 1.5 × 7 = 48.0
        assertThat(p.projection7jKg()).isEqualByComparingTo(BigDecimal.valueOf(48.0));
        assertThat(p.nombreMesures()).isEqualTo(6);
    }

    @Test
    @DisplayName("série stable : tendance STABLE")
    void tendanceStable() {
        when(mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS))
                .thenReturn(serieJournaliere(30.0, 0.0, 5));

        PrevisionRecolte p = service.previsions().get(0);

        assertThat(p.tendance()).isEqualTo(PrevisionRecolte.STABLE);
        assertThat(p.tendanceKgParJour()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("série en baisse : tendance BAISSE")
    void tendanceBaisse() {
        when(mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS))
                .thenReturn(serieJournaliere(30.0, -0.8, 4));

        PrevisionRecolte p = service.previsions().get(0);

        assertThat(p.tendance()).isEqualTo(PrevisionRecolte.BAISSE);
    }

    @Test
    @DisplayName("moins de deux mesures : tendance INCONNUE")
    void insuffisant() {
        when(mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS))
                .thenReturn(serieJournaliere(30.0, 1.0, 1));

        PrevisionRecolte p = service.previsions().get(0);

        assertThat(p.tendance()).isEqualTo(PrevisionRecolte.INCONNUE);
        assertThat(p.tendanceKgParJour()).isNull();
        assertThat(p.poidsActuelKg()).isEqualByComparingTo(BigDecimal.valueOf(30.0));
    }
}
