package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Site;
import com.zumm.repository.SiteRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.dto.MeteoReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Tests unitaires du contexte meteo et de ses previsions (US-029). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeteoServiceTest {

    @Mock
    private SiteRepository sites;

    @Mock
    private FournisseurMeteo fournisseur;

    @Mock
    private PolitiquePositions positions;

    private MeteoService service(String mode) {
        Site site = new Site("Rucher de Sidi Thabet", null,
                new BigDecimal("36.8000"), new BigDecimal("10.2000"), LocalDate.of(2026, 4, 1));
        when(sites.findById(1L)).thenReturn(Optional.of(site));
        // La politique arrondit au centieme de degre : c'est ce que voit l'appelant.
        when(positions.masquer(site.getLatitude(), site.getLongitude()))
                .thenReturn(new BigDecimal[] {new BigDecimal("36.80"), new BigDecimal("10.20")});
        return new MeteoService(sites, fournisseur, positions, mode);
    }

    @Test
    @DisplayName("les previsions du fournisseur sont rendues telles quelles")
    void previsionsDuFournisseur() {
        MeteoService service = service("auto");
        when(fournisseur.releve(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(
                new FournisseurMeteo.Releve(
                        new FournisseurMeteo.Meteo(21.5, 60, 12.0),
                        List.of(new FournisseurMeteo.Jour(LocalDate.of(2026, 8, 18),
                                14.0, 29.0, 0.0, 18.5)))));

        MeteoReponse reponse = service.pourSite(1L, 7);

        assertThat(reponse.source()).isEqualTo("open-meteo");
        assertThat(reponse.temperatureCelsius()).isEqualTo(21.5);
        assertThat(reponse.previsions()).singleElement().satisfies(jour -> {
            assertThat(jour.date()).isEqualTo(LocalDate.of(2026, 8, 18));
            assertThat(jour.temperatureMinCelsius()).isEqualTo(14.0);
            assertThat(jour.temperatureMaxCelsius()).isEqualTo(29.0);
            assertThat(jour.ventMaxKmh()).isEqualTo(18.5);
        });
    }

    @Test
    @DisplayName("la position exposee est masquee, la position interrogee ne l'est pas")
    void interrogeLaPositionExacte() {
        MeteoService service = service("auto");
        when(fournisseur.releve(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.of(
                new FournisseurMeteo.Releve(new FournisseurMeteo.Meteo(20.0, 55, 9.0), List.of())));

        MeteoReponse reponse = service.pourSite(1L, 3);

        ArgumentCaptor<Double> latitude = ArgumentCaptor.forClass(Double.class);
        verify(fournisseur).releve(latitude.capture(), anyDouble(), anyInt());
        assertThat(latitude.getValue()).isEqualTo(36.8);
        assertThat(reponse.latitude()).isEqualTo(36.80);
    }

    @Test
    @DisplayName("un fournisseur indisponible retombe sur une simulation, previsions comprises")
    void repliSurSimulation() {
        MeteoService service = service("auto");
        when(fournisseur.releve(anyDouble(), anyDouble(), anyInt())).thenReturn(Optional.empty());

        MeteoReponse reponse = service.pourSite(1L, 5);

        assertThat(reponse.source()).isEqualTo("simulation");
        assertThat(reponse.previsions()).hasSize(5);
        // Un ecran a moitie rempli serait plus deroutant qu'un ecran ouvertement simule.
        assertThat(reponse.previsions()).allSatisfy(jour -> {
            assertThat(jour.temperatureMinCelsius()).isLessThan(jour.temperatureMaxCelsius());
            assertThat(jour.date()).isNotNull();
        });
    }

    @Test
    @DisplayName("le mode simulation n'appelle jamais le fournisseur externe")
    void modeSimulationHorsLigne() {
        MeteoService service = service("simulation");

        MeteoReponse reponse = service.pourSite(1L, 2);

        assertThat(reponse.source()).isEqualTo("simulation");
        verify(fournisseur, org.mockito.Mockito.never()).releve(anyDouble(), anyDouble(), anyInt());
    }

    @Test
    @DisplayName("la simulation est deterministe : deux appels rendent la meme serie")
    void simulationDeterministe() {
        MeteoService service = service("simulation");

        assertThat(service.pourSite(1L, 4).previsions())
                .isEqualTo(service.pourSite(1L, 4).previsions());
    }

    @Test
    @DisplayName("l'horizon demande est borne, jamais rejete")
    void horizonBorne() {
        MeteoService service = service("simulation");

        assertThat(service.pourSite(1L, 99).previsions()).hasSize(MeteoService.JOURS_MAX);
        assertThat(service.pourSite(1L, -3).previsions()).isEmpty();
    }
}
