package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.domain.Visite;
import com.zumm.repository.AlerteRepository;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.SyntheseReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests unitaires de la synthèse et du ROI (US-015, US-036). */
@ExtendWith(MockitoExtension.class)
class SyntheseServiceTest {

    @Mock private VisiteRepository visites;
    @Mock private MesureRepository mesures;
    @Mock private RucheRepository ruches;
    @Mock private AlerteRepository alertes;

    @Test
    @DisplayName("agrège visites, production et calcule le ROI indicatif")
    void agregeEtCalculeRoi() {
        Ruche ruche = new Ruche("M", null, null, EtatRuche.CREEE);
        Agent agent = new Agent("Ava", RoleAgent.APICULTEUR, null);
        Visite v1 = new Visite(ruche, agent, LocalDate.of(2026, 6, 1), RaisonVisite.CONTROLE);
        v1.setProductivite(2);
        Visite v2 = new Visite(ruche, agent, LocalDate.of(2026, 6, 2), RaisonVisite.RECOLTE);
        v2.setProductivite(3);

        when(ruches.count()).thenReturn(2L);
        when(visites.findAll()).thenReturn(List.of(v1, v2));
        when(mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS))
                .thenReturn(List.of(new Mesure(
                        new MesureId(1L, TypeIndicateur.POIDS, Instant.parse("2026-06-02T10:00:00Z")),
                        BigDecimal.valueOf(20))));
        when(alertes.findByOuverteTrueOrderByOuverteLeDesc()).thenReturn(List.of());

        SyntheseReponse s = new SyntheseService(visites, mesures, ruches, alertes).synthese();

        assertThat(s.nombreRuches()).isEqualTo(2);
        assertThat(s.nombreVisites()).isEqualTo(2);
        assertThat(s.visitesParRaison()).containsEntry("controle", 1L).containsEntry("recolte", 1L);
        assertThat(s.productiviteMoyenne()).isEqualTo(2.5);
        assertThat(s.poidsTotalActuelKg()).isEqualByComparingTo(BigDecimal.valueOf(20).setScale(2));
        // Valorisation = 20 kg × 12 € = 240 € ; coût = 2 visites × 25 € = 50 €.
        assertThat(s.roi().valeurProductionEur()).isEqualByComparingTo("240.00");
        assertThat(s.roi().coutInterventionsEur()).isEqualByComparingTo("50.00");
        // ROI = (240 − 50) / 50 × 100 = 380 %.
        assertThat(s.roi().roiPourcent()).isEqualTo(380.0);
    }
}
