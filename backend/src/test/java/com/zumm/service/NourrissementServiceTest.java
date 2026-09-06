package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.Nourrissement;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.NourrissementRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.NourrissementCorps;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Registre des nourrissements (SPRINT-20, lot 3 du plan de couverture).
 *
 * <p>79,2 % d'instructions et <strong>75,0 % de branches</strong>. L'écart porte
 * presque entièrement sur une règle que la base <em>ne peut pas</em> tenir, et
 * dont la javadoc du service dit pourquoi :
 *
 * <blockquote>« 3 kg de sirop » et « 3 litres de candi » sont des saisies
 * plausibles au clavier et absurdes au rucher. Le CHECK porte sur chaque colonne
 * séparément, pas sur leur <strong>accord</strong> — et un bilan de saison
 * construit sur des unités mélangées est faux sans jamais paraître l'être.
 * </blockquote>
 *
 * <p>C'est la définition même d'une règle qui appartient au service : elle a un
 * message à expliquer, et personne d'autre ne peut la porter.
 */
@ExtendWith(MockitoExtension.class)
class NourrissementServiceTest {

    @Mock private NourrissementRepository nourrissements;
    @Mock private RucheRepository ruches;
    @Mock private AgentRepository agents;
    @Mock private VisiteRepository visites;
    @Mock private OperationsLotService lots;

    private NourrissementService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 9, 12);

    @BeforeEach
    void monter() {
        service = new NourrissementService(nourrissements, ruches, agents, visites, lots);
        lenient().when(nourrissements.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Ruche ruche() {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(5L);
        lenient().when(r.getModele()).thenReturn("Dadant 10");
        return r;
    }

    private static Agent agent() {
        Agent a = mock(Agent.class);
        lenient().when(a.getId()).thenReturn(9L);
        lenient().when(a.getNom()).thenReturn("Nefzi");
        return a;
    }

    private static NourrissementCorps corps(String type, String unite) {
        return new NourrissementCorps(5L, 9L, null, JOUR, type,
                new BigDecimal("3"), unite, "hivernage", null);
    }

    private void tenantComplet() {
        // Mocks construits AVANT le when() : en fabriquer un entre when() et
        // thenReturn() laisse le stub inachevé (UnfinishedStubbingException).
        Optional<Ruche> r = Optional.of(ruche());
        Optional<Agent> a = Optional.of(agent());
        when(ruches.findById(5L)).thenReturn(r);
        when(agents.findById(9L)).thenReturn(a);
    }

    // ── L'accord entre l'aliment et son unité ───────────────────────────────

    @ParameterizedTest(name = "{0} en {1} est refusé")
    @CsvSource({
        "sirop_1_1, kg", "sirop_2_1, g", "eau, kg", "miel, g",
    })
    @DisplayName("un apport LIQUIDE se mesure en volume, jamais en masse")
    void liquideEnMasse(String type, String unite) {
        tenantComplet();

        assertThatThrownBy(() -> service.enregistrer(corps(type, unite)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("liquide")
                .hasMessageContaining(type);
        verify(nourrissements, never()).save(any());
    }

    @ParameterizedTest(name = "{0} en {1} est refusé")
    @CsvSource({
        "candi, l", "candi, ml", "pollen, l", "substitut_pollen, ml",
    })
    @DisplayName("un apport SOLIDE se mesure en masse, jamais en volume")
    void solideEnVolume(String type, String unite) {
        tenantComplet();

        assertThatThrownBy(() -> service.enregistrer(corps(type, unite)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("solide")
                .hasMessageContaining("kg ou g");
    }

    @ParameterizedTest(name = "{0} en {1} passe")
    @CsvSource({
        "sirop_1_1, l", "sirop_2_1, ml", "eau, l", "miel, ml",
        "candi, kg", "pollen, g", "substitut_pollen, kg",
    })
    @DisplayName("les sept aliments du référentiel passent avec leur unité")
    void accordsValides(String type, String unite) {
        tenantComplet();

        // Les sept valeurs du @Pattern du DTO sont exercées : ajouter un
        // huitième aliment sans le classer liquide ou solide le ferait passer
        // avec n'importe quelle unité, silencieusement.
        assertThat(service.enregistrer(corps(type, unite))).isNotNull();
    }

    // ── Rattachements ───────────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche ou un agent hors tenant est refusé en 400, jamais en 500")
    void rattachementsInconnus() {
        when(ruches.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enregistrer(corps("candi", "kg")))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Ruche inconnue dans ce tenant : 5");

        Optional<Ruche> presente = Optional.of(ruche());
        when(ruches.findById(5L)).thenReturn(presente);
        when(agents.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enregistrer(corps("candi", "kg")))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Agent inconnu dans ce tenant : 9");
    }

    @Test
    @DisplayName("l'unité est vérifiée APRÈS les rattachements")
    void ordreDesControles() {
        when(ruches.findById(5L)).thenReturn(Optional.empty());

        // Un apport incohérent sur une ruche inconnue doit nommer la ruche :
        // c'est la faute la plus en amont, et corriger l'unité ne servirait à
        // rien tant que la ruche n'existe pas.
        assertThatThrownBy(() -> service.enregistrer(corps("candi", "l")))
                .hasMessageContaining("Ruche inconnue");
    }

    @Test
    @DisplayName("une visite absente laisse l'apport non rattaché, sans erreur")
    void visiteAbsente() {
        tenantComplet();

        assertThat(service.enregistrer(corps("candi", "kg")).visiteId()).isNull();
        verify(visites, never()).findById(any());
    }

    @Test
    @DisplayName("une visite désignée mais inconnue est refusée ; une visite connue est rattachée")
    void visite() {
        tenantComplet();
        NourrissementCorps avecVisite = new NourrissementCorps(5L, 9L, 3L, JOUR, "candi",
                new BigDecimal("3"), "kg", null, null);

        when(visites.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enregistrer(avecVisite))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Visite inconnue dans ce tenant : 3");

        Visite v = mock(Visite.class);
        when(v.getId()).thenReturn(3L);
        when(visites.findById(3L)).thenReturn(Optional.of(v));
        assertThat(service.enregistrer(avecVisite).visiteId()).isEqualTo(3L);
    }

    // ── Le lot ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le lot rejoue enregistrer() par ruche, il n'a pas sa propre implémentation")
    void lotRejoueLUnitaire() {
        tenantComplet();
        NourrissementCorps modele = new NourrissementCorps(999L, 9L, null, JOUR, "candi",
                new BigDecimal("3"), "kg", null, null);

        // C'est l'opération qui se saisit le plus souvent en lot : le sirop se
        // pose rucher par rucher, jamais colonie par colonie. L'identifiant du
        // MODÈLE (999) est ignoré au profit de la ruche du lot.
        service.enregistrerPour(ruche(), modele);

        verify(nourrissements).save(any(Nourrissement.class));
        verify(ruches).findById(5L);
    }

    // ── Lectures et suppression ─────────────────────────────────────────────

    @Test
    @DisplayName("le registre d'une ruche inconnue est un 404, pas une liste vide")
    void registreRucheInconnue() {
        when(ruches.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registre(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("le registre mappe les apports du plus récent au plus ancien")
    void registre() {
        Optional<Ruche> presente = Optional.of(ruche());
        Nourrissement n = new Nourrissement(ruche(), agent(), JOUR, "candi",
                new BigDecimal("3"), "kg");
        when(ruches.findById(5L)).thenReturn(presente);
        when(nourrissements.findByRuche_IdOrderByDateApportDescIdDesc(5L)).thenReturn(List.of(n));

        assertThat(service.registre(5L)).singleElement()
                .satisfies(r -> assertThat(r.typeAliment()).isEqualTo("candi"));
    }

    @Test
    @DisplayName("un apport inconnu est refusé en 404 ; sinon l'entité est supprimée")
    void suppression() {
        when(nourrissements.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);

        Nourrissement n = new Nourrissement(ruche(), agent(), JOUR, "candi",
                new BigDecimal("3"), "kg");
        Optional<Nourrissement> present = Optional.of(n);
        when(nourrissements.findById(1L)).thenReturn(present);
        service.supprimer(1L);
        verify(nourrissements).delete(n);
    }
}
