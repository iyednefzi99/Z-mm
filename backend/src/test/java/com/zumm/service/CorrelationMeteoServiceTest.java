package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.CorrelationMeteo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests de la correlation meteo ↔ production (SPRINT-22, lot A).
 *
 * <p>Le calcul de Pearson est trivial ; ce qui merite d'etre verrouille, c'est
 * le <strong>refus de conclure</strong>. Un tableau de bord qui affiche « lien
 * marque » sur huit visites fabrique une croyance que rien ne soutient, et cette
 * croyance survit longtemps a la donnee qui l'a produite.
 */
@ExtendWith(MockitoExtension.class)
class CorrelationMeteoServiceTest {

    @Mock
    private VisiteRepository visites;

    @Mock
    private RecolteRepository recoltes;

    private static final LocalDate DEBUT = LocalDate.of(2026, 3, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);

    /** Une visite avec meteo figee, sur la ruche 1. */
    private Visite visite(int jour, double temperature) {
        Ruche ruche = mock(Ruche.class);
        org.mockito.Mockito.lenient().when(ruche.getId()).thenReturn(1L);
        Visite v = mock(Visite.class);
        org.mockito.Mockito.lenient().when(v.getRuche()).thenReturn(ruche);
        org.mockito.Mockito.lenient().when(v.getDateVisite())
                .thenReturn(LocalDate.of(2026, 4, 1).plusDays(jour * 35L));
        org.mockito.Mockito.lenient().when(v.getMeteoSource()).thenReturn("open-meteo");
        org.mockito.Mockito.lenient().when(v.getMeteoTemperatureC())
                .thenReturn(BigDecimal.valueOf(temperature));
        org.mockito.Mockito.lenient().when(v.getMeteoHumiditePct()).thenReturn(null);
        org.mockito.Mockito.lenient().when(v.getMeteoVentKmh()).thenReturn(null);
        return v;
    }

    private Recolte recolte(LocalDate date, double kilos) {
        Ruche ruche = mock(Ruche.class);
        org.mockito.Mockito.lenient().when(ruche.getId()).thenReturn(1L);
        Recolte r = mock(Recolte.class);
        org.mockito.Mockito.lenient().when(r.getRuche()).thenReturn(ruche);
        org.mockito.Mockito.lenient().when(r.getDateRecolte()).thenReturn(date);
        org.mockito.Mockito.lenient().when(r.getQuantiteKg()).thenReturn(BigDecimal.valueOf(kilos));
        return r;
    }

    private CorrelationMeteo temperature(List<Visite> v, List<Recolte> r) {
        when(visites.findByDateVisiteBetweenOrderByDateVisiteAsc(any(), any())).thenReturn(v);
        org.mockito.Mockito.lenient().when(recoltes.findByOrderByDateRecolteDescIdDesc())
                .thenReturn(r);
        return new CorrelationMeteoService(visites, recoltes).calculer(DEBUT, FIN).stream()
                .filter(c -> "temperature".equals(c.indicateur()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("sans donnee, le coefficient n'existe pas — et ne vaut surtout pas zero")
    void aucuneDonnee() {
        CorrelationMeteo c = temperature(List.of(), List.of());

        // Zero dirait « aucun lien mesure », ce qui est une affirmation. Ici on
        // n'a rien mesure du tout.
        assertThat(c.coefficient()).isNull();
        assertThat(c.echantillon()).isZero();
        assertThat(c.interpretation()).isEqualTo("echantillon_insuffisant");
    }

    @Test
    @DisplayName("un lien parfait rend 1, mais reste non interprete sur un petit echantillon")
    void lienParfaitMaisEchantillonCourt() {
        List<Visite> v = new ArrayList<>();
        List<Recolte> r = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            v.add(visite(i, 15 + i));
            r.add(recolte(LocalDate.of(2026, 4, 1).plusDays(i * 35L + 5), 10 + i));
        }

        CorrelationMeteo c = temperature(v, r);

        assertThat(c.coefficient()).isEqualByComparingTo("1.0");
        assertThat(c.echantillon()).isEqualTo(4);
        // Le seuil d'echantillon prime sur la force du lien : quatre points
        // alignes ne demontrent rien, et « lien marque » serait un mensonge poli.
        assertThat(c.interpretation()).isEqualTo("echantillon_insuffisant");
    }

    @Test
    @DisplayName("au-dela du seuil d'echantillon, le lien est enfin qualifie")
    void lienQualifie() {
        List<Visite> v = new ArrayList<>();
        List<Recolte> r = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            v.add(visite(i, 12 + i));
            r.add(recolte(LocalDate.of(2026, 4, 1).plusDays(i * 35L + 5), 5 + i * 2));
        }

        CorrelationMeteo c = temperature(v, r);

        assertThat(c.echantillon()).isEqualTo(14);
        assertThat(c.interpretation()).isEqualTo("lien_marque_positif");
    }

    @Test
    @DisplayName("une temperature constante ne donne pas un lien nul, mais pas de lien du tout")
    void varianceNulle() {
        List<Visite> v = new ArrayList<>();
        List<Recolte> r = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            v.add(visite(i, 20));
            r.add(recolte(LocalDate.of(2026, 4, 1).plusDays(i * 35L + 5), 10 + i));
        }

        CorrelationMeteo c = temperature(v, r);

        // Le denominateur de Pearson est nul : le coefficient n'est pas defini.
        // Rendre 0 laisserait croire a une absence de lien MESUREE.
        assertThat(c.coefficient()).isNull();
        assertThat(c.interpretation()).isEqualTo("variance_nulle");
    }

    @Test
    @DisplayName("une visite sans recolte compte pour zero kilo, pas pour rien")
    void visiteSansRecolte() {
        List<Visite> v = List.of(visite(0, 18), visite(1, 25));

        CorrelationMeteo c = temperature(v, List.of());

        // Ecarter ces visites ne garderait que celles suivies d'une recolte, et
        // reviendrait a demander « quand on recolte, recolte-t-on ? ».
        assertThat(c.echantillon()).isEqualTo(2);
    }
}
