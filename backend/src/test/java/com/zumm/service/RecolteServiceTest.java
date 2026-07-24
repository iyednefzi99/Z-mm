package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.dto.RecolteCorps;
import com.zumm.web.dto.RecolteReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests unitaires de la génération du lot et du QR (US-033, US-036). */
@ExtendWith(MockitoExtension.class)
class RecolteServiceTest {

    @Mock private RecolteRepository recoltes;
    @Mock private RucheRepository ruches;

    @Test
    @DisplayName("génère un lot lisible ZUMM-<ruche>-<jour>-<séquence> et son QR payload")
    void genereLotEtQr() {
        Ruche ruche = mock(Ruche.class);
        when(ruche.getId()).thenReturn(5L);
        when(ruche.getModele()).thenReturn("Dadant");
        LocalDate date = LocalDate.of(2026, 7, 15);
        when(ruches.findById(5L)).thenReturn(Optional.of(ruche));
        when(recoltes.countByRuche_IdAndDateRecolte(eq(5L), eq(date))).thenReturn(0L);
        when(recoltes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecolteReponse r = new RecolteService(recoltes, ruches)
                .creer(new RecolteCorps(5L, date, new BigDecimal("18.500"), "Toutes fleurs", null));

        assertThat(r.lot()).isEqualTo("ZUMM-5-20260715-01");
        assertThat(r.qrPayload()).isEqualTo("zumm:tracabilite:ZUMM-5-20260715-01");
        assertThat(r.rucheModele()).isEqualTo("Dadant");
    }

    @Test
    @DisplayName("incrémente la séquence du lot pour une 2ᵉ récolte le même jour")
    void incrementeSequence() {
        Ruche ruche = mock(Ruche.class);
        when(ruche.getId()).thenReturn(5L);
        when(ruche.getModele()).thenReturn("Dadant");
        LocalDate date = LocalDate.of(2026, 7, 15);
        when(ruches.findById(5L)).thenReturn(Optional.of(ruche));
        when(recoltes.countByRuche_IdAndDateRecolte(eq(5L), eq(date))).thenReturn(1L);
        when(recoltes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecolteReponse r = new RecolteService(recoltes, ruches)
                .creer(new RecolteCorps(5L, date, BigDecimal.ONE, null, null));

        assertThat(r.lot()).isEqualTo("ZUMM-5-20260715-02");
    }
}
