package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.domain.Site;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.ZoneTraiteeRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.ExpositionRucher;
import com.zumm.web.dto.ZoneTraiteeCorps;
import com.zumm.web.dto.ZoneTraiteeReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Exposition aux zones traitées déclarées (SPRINT-33, lot K).
 *
 * <p>Cette classe ferme une ligne restée <strong>partielle</strong> depuis le
 * SPRINT-32, et le motif d'alors était juste : aucune couche ouverte ne dit ce
 * qui a été épandu ni quand. Ce qui est testé ici est donc moins un calcul qu'un
 * ensemble de <em>bornes</em> — celles qui empêchent une couche déclarative de
 * se faire passer pour une mesure.
 *
 * <ol>
 *   <li>Le <strong>silence n'est pas une garantie</strong> : une exposition sans
 *       déclaration rend zéro et {@code null}, pas « aucun traitement ».
 *   <li>Un <strong>délai de rentrée inconnu</strong> ne compte jamais comme
 *       écoulé — {@code null} n'est pas zéro, et le traiter comme tel serait
 *       rassurant à tort.
 *   <li>Le délai se compte en <strong>jours pleins</strong>, la borne étant
 *       inclusive : 24 h annoncées le mardi couvrent le mercredi entier, faute
 *       de connaître l'heure de l'épandage.
 *   <li>Une <strong>date future</strong> est refusée : le champ porte ce qui a
 *       été fait, pas ce qui est annoncé.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ZoneTraiteeServiceTest {

    @Mock private ZoneTraiteeRepository zones;
    @Mock private SiteRepository sites;
    @Mock private ConfigurationMetier configuration;

    private ZoneTraiteeService service;

    @BeforeEach
    void monter() {
        service = new ZoneTraiteeService(zones, sites, configuration);
    }

    private Site site(BigDecimal rayonKm) {
        Site site = new Site("Colline", null, new BigDecimal("48.10"),
                new BigDecimal("2.30"), null);
        ReflectionTestUtils.setField(site, "id", 7L);
        site.setRayonButinageKm(rayonKm);
        return site;
    }

    private void rayonParDefaut(int km) {
        SeuilsMetier seuils = SeuilsMetier.defauts();
        lenient().when(configuration.seuils()).thenReturn(seuils);
        assertThat(seuils.rayonButinageKm()).isPositive();
    }

    private static ZoneTraiteeReponse zone(LocalDate date, Integer delaiH) {
        return new ZoneTraiteeReponse(1L, date, "cyperméthrine", "voisin_declare", delaiH,
                null, new BigDecimal("3.20"));
    }

    private static ZoneTraiteeCorps corps(LocalDate date) {
        try {
            return new ZoneTraiteeCorps(
                    new ObjectMapper().readTree(
                            "{\"type\":\"Polygon\",\"coordinates\":[[[2,48],[2.01,48],"
                                    + "[2.01,48.01],[2,48.01],[2,48]]]}"),
                    date, "cyperméthrine", "voisin_declare", 24, null);
        } catch (Exception erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    // ─── Ce que le silence veut dire ────────────────────────────────────────

    @Test
    @DisplayName("aucune déclaration : zéro et null, jamais « aucun traitement »")
    void expositionSansDeclaration() {
        when(sites.findById(7L)).thenReturn(Optional.of(site(new BigDecimal("3.0"))));
        when(zones.autour(eq(7L), anyDouble())).thenReturn(List.of());
        when(zones.distanceMin(7L)).thenReturn(null);

        ExpositionRucher exposition = service.autour(7L, LocalDate.of(2026, 4, 20));

        assertThat(exposition.declarations()).isZero();
        // La date de dernière déclaration reste NULLE : c'est elle qui distingue
        // « rien ne m'a été déclaré » de « rien n'a été épandu ». Une exploitation
        // qui n'a jamais rien saisi le voit du premier coup d'œil.
        assertThat(exposition.derniereDeclaration()).isNull();
        assertThat(exposition.distanceMinM()).isNull();
        assertThat(exposition.sousDelaiRentree()).isZero();
        assertThat(exposition.zones()).isEmpty();
    }

    @Test
    @DisplayName("le rayon du rucher prime sur le défaut de configuration")
    void rayonDuRucher() {
        when(sites.findById(7L)).thenReturn(Optional.of(site(new BigDecimal("5.5"))));
        when(zones.autour(eq(7L), eq(5500.0))).thenReturn(List.of());
        when(zones.distanceMin(7L)).thenReturn(null);

        assertThat(service.autour(7L, LocalDate.of(2026, 4, 20)).rayonKm())
                .isEqualByComparingTo("5.5");
        // Le défaut n'est même pas lu : deux ruchers d'une même exploitation
        // n'ont pas le même environnement, et c'est tout l'objet de la V31.
        verify(configuration, never()).seuils();
    }

    @Test
    @DisplayName("sans rayon propre, le rucher retombe sur la configuration")
    void rayonParDefautApplique() {
        rayonParDefaut(3);
        when(sites.findById(7L)).thenReturn(Optional.of(site(null)));
        when(zones.autour(anyLong(), anyDouble())).thenReturn(List.of());
        when(zones.distanceMin(anyLong())).thenReturn(null);

        assertThat(service.autour(7L, LocalDate.of(2026, 4, 20)).rayonKm())
                .isEqualByComparingTo(
                        BigDecimal.valueOf(SeuilsMetier.defauts().rayonButinageKm()));
    }

    @Test
    @DisplayName("rucher inconnu : 404, pas une exposition vide")
    void rucherInconnu() {
        when(sites.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.autour(99L, LocalDate.now()))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    // ─── Le délai de rentrée ────────────────────────────────────────────────

    @Test
    @DisplayName("délai inconnu : jamais compté comme écoulé, et jamais comme en cours")
    void delaiInconnu() {
        // `null` n'est pas zéro. Le compter comme écoulé serait rassurant à tort ;
        // le compter comme en cours ferait clignoter chaque déclaration ancienne.
        assertThat(ZoneTraiteeService.sousDelaiRentree(
                zone(LocalDate.of(2026, 4, 1), null), LocalDate.of(2026, 4, 2))).isFalse();
    }

    @Test
    @DisplayName("24 h annoncées le mardi couvrent le mercredi entier")
    void delaiEnJoursPleins() {
        LocalDate mardi = LocalDate.of(2026, 4, 14);
        ZoneTraiteeReponse zone = zone(mardi, 24);

        assertThat(ZoneTraiteeService.sousDelaiRentree(zone, mardi)).isTrue();
        assertThat(ZoneTraiteeService.sousDelaiRentree(zone, mardi.plusDays(1))).isTrue();
        // La borne est INCLUSIVE au dernier jour, et exclusive au suivant :
        // réclamer l'heure de l'épandage ferait échouer la saisie la plus
        // courante — « ils ont traité hier ».
        assertThat(ZoneTraiteeService.sousDelaiRentree(zone, mardi.plusDays(2))).isFalse();
    }

    @Test
    @DisplayName("un délai de 6 h s'arrondit au jour, pas à rien")
    void delaiInferieurAUnJour() {
        LocalDate jour = LocalDate.of(2026, 4, 14);
        assertThat(ZoneTraiteeService.sousDelaiRentree(zone(jour, 6), jour)).isTrue();
        assertThat(ZoneTraiteeService.sousDelaiRentree(zone(jour, 6), jour.plusDays(1)))
                .isTrue();
        assertThat(ZoneTraiteeService.sousDelaiRentree(zone(jour, 6), jour.plusDays(2)))
                .isFalse();
    }

    @Test
    @DisplayName("le compte « sous délai » ne retient que les zones encore en cours")
    void compteSousDelai() {
        LocalDate jour = LocalDate.of(2026, 4, 20);
        when(sites.findById(7L)).thenReturn(Optional.of(site(new BigDecimal("3.0"))));
        when(zones.autour(eq(7L), anyDouble())).thenReturn(List.of(
                zone(jour.minusDays(1), 48),   // en cours
                zone(jour.minusDays(30), 48),  // écoulé
                zone(jour.minusDays(1), null)  // inconnu : ne compte pas
        ));
        when(zones.distanceMin(7L)).thenReturn(120.0);

        ExpositionRucher exposition = service.autour(7L, jour);

        assertThat(exposition.declarations()).isEqualTo(3);
        assertThat(exposition.sousDelaiRentree()).isEqualTo(1);
        assertThat(exposition.derniereDeclaration()).isEqualTo(jour.minusDays(1));
        assertThat(exposition.distanceMinM()).isEqualTo(120.0);
    }

    // ─── Ce que la déclaration refuse ───────────────────────────────────────

    @Test
    @DisplayName("une date future est refusée : le champ porte un constat, pas une annonce")
    void dateFutureRefusee() {
        assertThatThrownBy(() -> service.declarer(corps(LocalDate.now().plusDays(1))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("futur");

        // Rien n'est écrit : mélanger constat et annonce dans la même colonne
        // ferait perdre le sens de la lecture « sous délai de rentrée ».
        verify(zones, never()).inserer(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("une géométrie nulle est refusée avant d'atteindre PostGIS")
    void geometrieNulleRefusee() {
        ZoneTraiteeCorps sansGeometrie = new ZoneTraiteeCorps(
                new ObjectMapper().nullNode(), LocalDate.now(), null, "observe", null, null);

        assertThatThrownBy(() -> service.declarer(sansGeometrie))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("substance et note vides deviennent nulles, jamais des chaînes vides")
    void normalisation() {
        ZoneTraiteeCorps corps = new ZoneTraiteeCorps(corps(LocalDate.now()).geometrie(),
                LocalDate.now(), "   ", "observe", null, "  ");
        when(zones.inserer(any(), any(), eq(null), eq("observe"), eq(null), eq(null)))
                .thenReturn(42L);
        when(zones.lister()).thenReturn(List.of(new ZoneTraiteeReponse(42L, LocalDate.now(),
                null, "observe", null, null, BigDecimal.ONE)));

        assertThat(service.declarer(corps).id()).isEqualTo(42L);
    }

    @Test
    @DisplayName("supprimer une zone inconnue : 404 et non un silence")
    void suppressionInconnue() {
        when(zones.supprimer(5L)).thenReturn(0);

        assertThatThrownBy(() -> service.supprimer(5L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("lister délègue au dépôt, sans retraiter")
    void listage() {
        when(zones.lister()).thenReturn(List.of(zone(LocalDate.now(), 24)));

        assertThat(service.lister()).hasSize(1);
    }
}
