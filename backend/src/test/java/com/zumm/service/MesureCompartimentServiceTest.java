package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Compartiment;
import com.zumm.domain.MesureCompartiment;
import com.zumm.domain.MesureCompartimentId;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeCompartiment;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.CompartimentRepository;
import com.zumm.repository.MesureCompartimentRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MesureCompartimentCorps;
import com.zumm.web.dto.PoidsCompartiment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Poids par compartiment (SPRINT-26 lot F₁, lot 3 du plan de couverture).
 *
 * <p>75,4 % d'instructions, 80,0 % de branches. L'écart porte sur la
 * répartition — et c'est la partie où une branche fausse ne casse rien : elle
 * affiche un poids.
 *
 * <p>La javadoc du service dit ce qu'il <em>ne fait pas</em>, et c'est le plus
 * important : il ne somme pas les compartiments pour en déduire le poids de la
 * ruche, et il n'alimente ni les alertes de seuil, ni la prévision de récolte,
 * ni la détection d'anomalie. Additionner des pesées de hausses faites à des
 * moments différents produirait un poids <strong>qui n'a jamais existé</strong>,
 * et la prévision compterait deux fois le même miel. La répartition répond à une
 * autre question : <em>où</em> est le miel.
 */
@ExtendWith(MockitoExtension.class)
class MesureCompartimentServiceTest {

    @Mock private MesureCompartimentRepository mesures;
    @Mock private CompartimentRepository compartiments;
    @Mock private RucheRepository ruches;

    private MesureCompartimentService service;

    private static final Instant T = Instant.parse("2026-07-14T09:00:00Z");

    @BeforeEach
    void monter() {
        service = new MesureCompartimentService(mesures, compartiments, ruches);
        lenient().when(mesures.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Compartiment compartiment(long id, TypeCompartiment type, int cadres) {
        Compartiment c = mock(Compartiment.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getType()).thenReturn(type);
        lenient().when(c.getNbCadres()).thenReturn(cadres);
        return c;
    }

    private static MesureCompartiment pesee(long compartimentId, String valeur, Instant quand) {
        return new MesureCompartiment(
                new MesureCompartimentId(compartimentId, TypeIndicateur.POIDS, quand),
                new BigDecimal(valeur));
    }

    // ── Peser ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un poids négatif est refusé : une hausse ne pèse pas moins que rien")
    void poidsNegatif() {
        Optional<Compartiment> hausse = Optional.of(compartiment(2L, TypeCompartiment.HAUSSE, 9));
        when(compartiments.findById(2L)).thenReturn(hausse);

        // NUMERIC est signé : la base l'accepterait, et la courbe deviendrait
        // illisible sans qu'aucune erreur ne soit jamais levée.
        assertThatThrownBy(() -> service.peser(
                new MesureCompartimentCorps(2L, new BigDecimal("-1"), T)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("ne peut pas être négatif");
        verify(mesures, never()).save(any());
    }

    @Test
    @DisplayName("un poids nul est accepté : une hausse vide pèse zéro, et c'est une donnée")
    void poidsNul() {
        Optional<Compartiment> hausse = Optional.of(compartiment(2L, TypeCompartiment.HAUSSE, 9));
        when(compartiments.findById(2L)).thenReturn(hausse);

        assertThat(service.peser(new MesureCompartimentCorps(2L, BigDecimal.ZERO, T)).valeur())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("sans instant, la pesée est horodatée à maintenant")
    void instantAbsent() {
        Optional<Compartiment> hausse = Optional.of(compartiment(2L, TypeCompartiment.HAUSSE, 9));
        when(compartiments.findById(2L)).thenReturn(hausse);
        Instant avant = Instant.now();

        PoidsCompartiment p = service.peser(
                new MesureCompartimentCorps(2L, new BigDecimal("12.5"), null));

        // L'instant fait partie de la clé primaire de l'hypertable : le laisser
        // nul ferait échouer l'insertion sur une contrainte, pas sur un message.
        assertThat(p.instant()).isBetween(avant, Instant.now());
    }

    @Test
    @DisplayName("la réponse porte le type et le nombre de cadres, pas seulement le poids")
    void reponseComplete() {
        Optional<Compartiment> hausse = Optional.of(compartiment(2L, TypeCompartiment.HAUSSE, 9));
        when(compartiments.findById(2L)).thenReturn(hausse);

        // 12 kg sur une hausse de 9 cadres et 12 kg sur un corps de 10 ne se
        // lisent pas de la même façon.
        PoidsCompartiment p = service.peser(
                new MesureCompartimentCorps(2L, new BigDecimal("12.5"), T));

        assertThat(p.type()).isEqualTo("hausse");
        assertThat(p.nbCadres()).isEqualTo(9);
        assertThat(p.valeur()).isEqualByComparingTo("12.5");
        assertThat(p.instant()).isEqualTo(T);
    }

    @Test
    @DisplayName("un compartiment hors tenant est refusé en 400 qui le nomme")
    void compartimentInconnu() {
        when(compartiments.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.peser(
                new MesureCompartimentCorps(2L, BigDecimal.ONE, T)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Compartiment inconnu dans ce tenant : 2");
    }

    // ── La répartition ──────────────────────────────────────────────────────

    @Test
    @DisplayName("un compartiment jamais pesé FIGURE, avec un poids nul")
    void compartimentJamaisPese() {
        Compartiment corps = compartiment(1L, TypeCompartiment.CORPS, 10);
        Compartiment hausse = compartiment(2L, TypeCompartiment.HAUSSE, 9);
        Ruche r = mock(Ruche.class);
        List<Compartiment> composition = List.of(corps, hausse);
        Optional<Ruche> presente = Optional.of(r);
        when(r.getCompartiments()).thenReturn(composition);
        when(ruches.findById(5L)).thenReturn(presente);
        when(mesures.derniersParCompartiment(List.of(1L, 2L)))
                .thenReturn(List.of(pesee(1L, "22.0", T)));

        List<PoidsCompartiment> repartition = service.repartition(5L);

        // Le taire donnerait une répartition qui a l'air complète et qui ne
        // l'est pas ; afficher zéro serait pire encore — une hausse jamais pesée
        // n'est pas une hausse vide.
        assertThat(repartition).hasSize(2);
        assertThat(repartition.get(1).valeur()).isNull();
        assertThat(repartition.get(1).instant()).isNull();
        assertThat(repartition.get(1).nbCadres()).isEqualTo(9);
    }

    @Test
    @DisplayName("la répartition suit l'ordre de la COMPOSITION, pas celui des pesées")
    void ordreDeLaComposition() {
        Compartiment corps = compartiment(1L, TypeCompartiment.CORPS, 10);
        Compartiment hausse = compartiment(2L, TypeCompartiment.HAUSSE, 9);
        Ruche r = mock(Ruche.class);
        List<Compartiment> composition = List.of(corps, hausse);
        Optional<Ruche> presente = Optional.of(r);
        when(r.getCompartiments()).thenReturn(composition);
        when(ruches.findById(5L)).thenReturn(presente);
        when(mesures.derniersParCompartiment(List.of(1L, 2L)))
                .thenReturn(List.of(pesee(2L, "8.0", T), pesee(1L, "22.0", T)));

        // C'est l'empilement physique que l'apiculteur a devant lui ; le rendre
        // dans l'ordre des pesées ferait lire un corps au-dessus d'une hausse.
        assertThat(service.repartition(5L)).extracting(PoidsCompartiment::type)
                .containsExactly("corps", "hausse");
    }

    @Test
    @DisplayName("deux pesées du même compartiment : la première rendue gagne")
    void doublonDansLaRequete() {
        Compartiment corps = compartiment(1L, TypeCompartiment.CORPS, 10);
        Ruche r = mock(Ruche.class);
        List<Compartiment> composition = List.of(corps);
        Optional<Ruche> presente = Optional.of(r);
        when(r.getCompartiments()).thenReturn(composition);
        when(ruches.findById(5L)).thenReturn(presente);
        when(mesures.derniersParCompartiment(List.of(1L)))
                .thenReturn(List.of(pesee(1L, "22.0", T), pesee(1L, "21.0", T.minusSeconds(60))));

        // La fusion de clés est explicite (premier, second) -> premier : sans
        // elle, Collectors.toMap lèverait une IllegalStateException sur une
        // donnée que la requête peut légitimement rendre en double.
        assertThat(service.repartition(5L).get(0).valeur()).isEqualByComparingTo("22.0");
    }

    @Test
    @DisplayName("une ruche sans compartiment rend une liste vide sans interroger les mesures")
    void rucheSansComposition() {
        Ruche r = mock(Ruche.class);
        Optional<Ruche> presente = Optional.of(r);
        when(r.getCompartiments()).thenReturn(List.of());
        when(ruches.findById(5L)).thenReturn(presente);

        assertThat(service.repartition(5L)).isEmpty();
        verify(mesures, never()).derniersParCompartiment(any());
    }

    @Test
    @DisplayName("une ruche inconnue est refusée en 404")
    void rucheInconnue() {
        when(ruches.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.repartition(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    // ── La série ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la série porte le type et les cadres sur CHAQUE point")
    void serie() {
        Optional<Compartiment> hausse = Optional.of(compartiment(2L, TypeCompartiment.HAUSSE, 9));
        List<MesureCompartiment> points = List.of(pesee(2L, "8.0", T.minusSeconds(1800)),
                pesee(2L, "8.4", T));
        when(compartiments.findById(2L)).thenReturn(hausse);
        when(mesures.findByIdCompartimentIdAndIdInstantBetweenOrderByIdInstantAsc(
                2L, T.minusSeconds(3600), T)).thenReturn(points);

        List<PoidsCompartiment> serie = service.serie(2L, T.minusSeconds(3600), T);

        assertThat(serie).extracting(PoidsCompartiment::valeur)
                .containsExactly(new BigDecimal("8.0"), new BigDecimal("8.4"));
        assertThat(serie).allSatisfy(p -> {
            assertThat(p.type()).isEqualTo("hausse");
            assertThat(p.nbCadres()).isEqualTo(9);
        });
    }

    @Test
    @DisplayName("une fenêtre sans pesée rend une liste vide, pas une erreur")
    void serieVide() {
        Optional<Compartiment> hausse = Optional.of(compartiment(2L, TypeCompartiment.HAUSSE, 9));
        when(compartiments.findById(2L)).thenReturn(hausse);
        when(mesures.findByIdCompartimentIdAndIdInstantBetweenOrderByIdInstantAsc(
                any(), any(), any())).thenReturn(List.of());

        assertThat(service.serie(2L, T.minusSeconds(3600), T)).isEmpty();
    }

    @Test
    @DisplayName("la série d'un compartiment inconnu est un 404")
    void serieCompartimentInconnu() {
        when(compartiments.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.serie(999L, T.minusSeconds(3600), T))
                .isInstanceOf(RessourceIntrouvable.class);
    }
}
