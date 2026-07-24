package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.domain.Alerte;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.AlerteRepository;
import com.zumm.web.dto.AlerteReponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests unitaires de la logique d'hysteresis (US-018, US-036). Les seuils par
 * defaut fixent poids d'alerte = 15 kg, donc la marge d'hysteresis vaut 0,75 kg.
 */
@ExtendWith(MockitoExtension.class)
class SeuilAlerteServiceTest {

    @Mock
    private AlerteRepository alertes;

    @Mock
    private ConfigurationMetier configuration;

    private SeuilAlerteService service;
    private final Ruche ruche = new Ruche("M", null, null, EtatRuche.CREEE);

    @BeforeEach
    void init() {
        when(configuration.seuils()).thenReturn(SeuilsMetier.defauts());
        service = new SeuilAlerteService(alertes, configuration);
    }

    @Test
    @DisplayName("ouvre une alerte critique quand le poids passe sous le seuil")
    void ouvreAlerteSousLeSeuil() {
        when(alertes.findByRuche_IdAndTypeIndicateurAndOuverteTrue(any(), eq(TypeIndicateur.POIDS)))
                .thenReturn(Optional.empty());
        when(alertes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<AlerteReponse> resultat = service.evaluer(ruche, TypeIndicateur.POIDS, BigDecimal.valueOf(10));

        assertThat(resultat).hasSize(1);
        assertThat(resultat.get(0).niveau()).isEqualTo(Alerte.CRITIQUE);
        verify(alertes).save(any());
    }

    @Test
    @DisplayName("n'ouvre pas de doublon quand une alerte est déjà ouverte")
    void pasDeDoublonSiDejaOuverte() {
        Alerte existante = new Alerte(ruche, TypeIndicateur.POIDS, Alerte.CRITIQUE, "msg", BigDecimal.valueOf(9));
        when(alertes.findByRuche_IdAndTypeIndicateurAndOuverteTrue(any(), eq(TypeIndicateur.POIDS)))
                .thenReturn(Optional.of(existante));

        List<AlerteReponse> resultat = service.evaluer(ruche, TypeIndicateur.POIDS, BigDecimal.valueOf(11));

        assertThat(resultat).isEmpty();
        verify(alertes, never()).save(any());
    }

    @Test
    @DisplayName("ferme l'alerte quand le poids revient au-delà de la bande d'hystérésis")
    void fermeAlerteEnZoneSure() {
        Alerte existante = new Alerte(ruche, TypeIndicateur.POIDS, Alerte.CRITIQUE, "msg", BigDecimal.valueOf(9));
        when(alertes.findByRuche_IdAndTypeIndicateurAndOuverteTrue(any(), eq(TypeIndicateur.POIDS)))
                .thenReturn(Optional.of(existante));

        // 16 kg >= 15 + 0,75 : zone sûre → fermeture.
        List<AlerteReponse> resultat = service.evaluer(ruche, TypeIndicateur.POIDS, BigDecimal.valueOf(16));

        assertThat(resultat).hasSize(1);
        assertThat(existante.isOuverte()).isFalse();
        assertThat(existante.getFermeeLe()).isNotNull();
    }

    @Test
    @DisplayName("zone neutre (entre seuil et bande) : aucun changement d'état")
    void zoneNeutreNeChangeRien() {
        when(alertes.findByRuche_IdAndTypeIndicateurAndOuverteTrue(any(), eq(TypeIndicateur.POIDS)))
                .thenReturn(Optional.empty());

        // 15,2 kg est dans [15 ; 15,75) : ni alerte, ni fermeture.
        List<AlerteReponse> resultat = service.evaluer(ruche, TypeIndicateur.POIDS, BigDecimal.valueOf(15.2));

        assertThat(resultat).isEmpty();
        verify(alertes, never()).save(any());
    }

    @Test
    @DisplayName("l'activité n'a aucun seuil paramétré : jamais d'alerte")
    void activiteSansSeuil() {
        List<AlerteReponse> resultat = service.evaluer(ruche, TypeIndicateur.ACTIVITE, BigDecimal.valueOf(999));

        assertThat(resultat).isEmpty();
        verify(alertes, never()).save(any());
    }
}
