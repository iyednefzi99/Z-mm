package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Reine;
import com.zumm.domain.Ruche;
import com.zumm.domain.SerieElevage;
import com.zumm.repository.ReineRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SerieElevageRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.Genealogie;
import com.zumm.web.dto.ReineElevage;
import com.zumm.web.dto.ReineElevageCorps;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Élevage et généalogie des reines (SPRINT-29, lot 3 du plan de couverture).
 *
 * <p>La plus grosse classe restante du paquet : 179 instructions et 21 branches
 * manquantes. Trois décisions du SPRINT-29 s'y jouent, et deux d'entre elles
 * décident de ce que l'arbre affiche :
 *
 * <ol>
 *   <li><strong>Une filiation ne peut pas se refermer sur elle-même.</strong> La
 *       base n'en vérifie qu'un pas ({@code ck_reine_mere}) ; le cycle à
 *       plusieurs pas se produit bel et bien — on corrige une filiation saisie à
 *       l'envers. Un arbre circulaire ne se lit pas, il boucle.</li>
 *   <li><strong>Les deux sens du parcours n'ont pas la même forme</strong> : les
 *       mères font une chaîne, les filles un arbre. Une reine déjà visitée
 *       arrête proprement la remontée — sans quoi une base reprise d'ailleurs
 *       rendrait quinze fois la même reine.</li>
 *   <li><strong>Le fournisseur n'a de sens que sur une reine achetée</strong>, et
 *       il est effacé plutôt que refusé quand l'origine change après coup.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ElevageServiceTest {

    @Mock private ReineRepository reines;
    @Mock private SerieElevageRepository series;
    @Mock private RucheRepository ruches;
    @Mock private IndexGenetiqueService index;

    private ElevageService service;

    @BeforeEach
    void monter() {
        service = new ElevageService(reines, series, ruches, index);
        lenient().when(reines.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(reines.findByMere_IdOrderByIdAsc(any())).thenReturn(List.of());
    }

    /** Une reine mockée : son identifiant n'est pas assignable autrement. */
    private static Reine reine(Long id, Reine mere) {
        Reine r = mock(Reine.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getMere()).thenReturn(mere);
        lenient().when(r.getOrigine()).thenReturn("elevage");
        lenient().when(r.getStatut()).thenReturn("en_service");
        return r;
    }

    private static ReineElevageCorps corps(String code, Long mereId, String origine,
            String fournisseur) {
        return new ReineElevageCorps(code, mereId, null, null, null, origine, fournisseur,
                "buckfast", 2026, "blanc", null, null, null, null, null, null, null, null);
    }

    // ── Le code unique ──────────────────────────────────────────────────────

    @Test
    @DisplayName("un code déjà pris est refusé à la création, avec le code en clair")
    void codeDejaPrisALaCreation() {
        when(reines.existsByCode("R-2026-01")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(corps("R-2026-01", null, "elevage", null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("R-2026-01");
        verify(reines, never()).save(any());
    }

    @Test
    @DisplayName("un code vide ou absent ne déclenche aucune vérification d'unicité")
    void codeAbsent() {
        service.creer(corps(null, null, "elevage", null));
        service.creer(corps("   ", null, "elevage", null));

        // Pas de code, pas de collision : interroger la base pour rien coûterait
        // une requête par saisie.
        verify(reines, never()).existsByCode(any());
    }

    @Test
    @DisplayName("garder son propre code en mise à jour n'est pas un doublon")
    void memeCodeEnMiseAJour() {
        Reine existante = reine(1L, null);
        when(existante.getCode()).thenReturn("R-2026-01");
        when(reines.findById(1L)).thenReturn(Optional.of(existante));

        // Sans cette comparaison, toute mise à jour d'une reine codée serait
        // refusée comme un doublon d'elle-même.
        assertThatCode(() -> service.mettreAJour(1L, corps("R-2026-01", null, "elevage", null)))
                .doesNotThrowAnyException();
        verify(reines, never()).existsByCode(any());
    }

    @Test
    @DisplayName("prendre le code d'une autre reine est refusé en mise à jour")
    void codeDUneAutreEnMiseAJour() {
        Reine existante = reine(1L, null);
        when(existante.getCode()).thenReturn("R-2026-01");
        when(reines.findById(1L)).thenReturn(Optional.of(existante));
        when(reines.existsByCode("R-2026-02")).thenReturn(true);

        assertThatThrownBy(() -> service.mettreAJour(1L, corps("R-2026-02", null, "elevage", null)))
                .isInstanceOf(RequeteInvalide.class);
    }

    // ── Le cycle de filiation ───────────────────────────────────────────────

    @Nested
    @DisplayName("filiation")
    class Filiation {

        @Test
        @DisplayName("une reine ne peut pas descendre d'elle-même, même à trois pas")
        void cycleRefuse() {
            // fille -> mere -> grandMere, et l'on tenterait de donner « fille »
            // comme mère de… fille. Le cas réel : une filiation saisie à
            // l'envers, qu'on corrige.
            Reine fille = reine(1L, null);
            Reine grandMere = reine(3L, fille);
            Reine mere = reine(2L, grandMere);
            when(reines.findById(1L)).thenReturn(Optional.of(fille));
            when(reines.findById(2L)).thenReturn(Optional.of(mere));

            assertThatThrownBy(() ->
                    service.mettreAJour(1L, corps(null, 2L, "elevage", null)))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("descendre la reine d'elle-meme");
        }

        @Test
        @DisplayName("une filiation ordinaire est acceptée")
        void filiationValide() {
            Reine fille = reine(1L, null);
            Reine mere = reine(2L, null);
            when(reines.findById(1L)).thenReturn(Optional.of(fille));
            when(reines.findById(2L)).thenReturn(Optional.of(mere));

            service.mettreAJour(1L, corps(null, 2L, "elevage", null));

            verify(fille).setMere(mere);
        }

        @Test
        @DisplayName("une mère inconnue dans ce tenant est refusée")
        void mereInconnue() {
            when(reines.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(corps(null, 99L, "elevage", null)))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("une reine nouvelle n'a pas d'identifiant : aucun cycle possible")
        void creationSansCycle() {
            Reine mere = reine(2L, null);
            when(reines.findById(2L)).thenReturn(Optional.of(mere));

            // À la création, `reine.getId()` est nul : la recherche de cycle est
            // sautée, et c'est correct — une reine qui n'existe pas encore ne
            // peut figurer dans aucune ascendance.
            assertThatCode(() -> service.creer(corps(null, 2L, "elevage", null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("aucune mère : le champ est vidé, pas laissé en place")
        void sansMere() {
            Reine existante = reine(1L, reine(2L, null));
            when(reines.findById(1L)).thenReturn(Optional.of(existante));

            service.mettreAJour(1L, corps(null, null, "elevage", null));

            verify(existante).setMere(null);
        }
    }

    // ── Le fournisseur ──────────────────────────────────────────────────────

    @Test
    @DisplayName("le fournisseur n'est conservé que sur une reine achetée")
    void fournisseurSelonOrigine() {
        Reine achetee = reine(1L, null);
        Reine elevee = reine(2L, null);
        when(achetee.getOrigine()).thenReturn("achat");
        when(elevee.getOrigine()).thenReturn("elevage");
        when(reines.findById(1L)).thenReturn(Optional.of(achetee));
        when(reines.findById(2L)).thenReturn(Optional.of(elevee));

        service.mettreAJour(1L, corps(null, null, "achat", "Rucher du Sud"));
        service.mettreAJour(2L, corps(null, null, "elevage", "Rucher du Sud"));

        // Effacé plutôt que refusé : l'utilisateur a simplement changé d'origine
        // après coup, et faire échouer sa saisie ne l'aiderait pas.
        verify(achetee).setFournisseur("Rucher du Sud");
        verify(elevee).setFournisseur(null);
    }

    @Test
    @DisplayName("l'origine par défaut est l'ignorance, pas l'élevage maison")
    void origineParDefaut() {
        assertThat(corps(null, null, null, null).origineOuInconnue()).isEqualTo("inconnue");
    }

    // ── Généalogie ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("généalogie")
    class GenealogieTest {

        @Test
        @DisplayName("les mères font une chaîne, du plus proche au plus lointain")
        void chaineDesMeres() {
            Reine arriereGrandMere = reine(4L, null);
            Reine grandMere = reine(3L, arriereGrandMere);
            Reine mere = reine(2L, grandMere);
            Reine depart = reine(1L, mere);
            when(reines.findById(1L)).thenReturn(Optional.of(depart));

            Genealogie g = service.genealogie(1L);

            assertThat(g.ascendants()).extracting(Genealogie.Noeud::id)
                    .containsExactly(2L, 3L, 4L);
            assertThat(g.ascendants()).extracting(Genealogie.Noeud::profondeur)
                    .containsExactly(1, 2, 3);
            assertThat(g.ascendants().get(0).parentId()).isEqualTo(3L);
            assertThat(g.ascendants().get(2).parentId()).isNull();
        }

        @Test
        @DisplayName("une reine sans mère a une ascendance vide, pas une erreur")
        void aucuneAscendance() {
            Reine depart = reine(1L, null);
            when(reines.findById(1L)).thenReturn(Optional.of(depart));

            assertThat(service.genealogie(1L).ascendants()).isEmpty();
        }

        @Test
        @DisplayName("un cycle hérité d'ailleurs arrête la remontée au lieu de boucler")
        void cycleDansLAscendance() {
            // Une base reprise d'ailleurs peut porter le cycle que ce service
            // n'écrit jamais. La garde de profondeur suffirait à ne pas boucler
            // indéfiniment, mais elle rendrait quinze fois la même reine.
            Reine[] boucle = new Reine[2];
            Reine a = mock(Reine.class);
            Reine b = mock(Reine.class);
            lenient().when(a.getId()).thenReturn(1L);
            lenient().when(b.getId()).thenReturn(2L);
            lenient().when(a.getMere()).thenReturn(b);
            lenient().when(b.getMere()).thenReturn(a);
            boucle[0] = a;
            boucle[1] = b;
            when(reines.findById(1L)).thenReturn(Optional.of(a));

            List<Genealogie.Noeud> ascendants = service.genealogie(1L).ascendants();

            assertThat(ascendants).hasSize(1);
            assertThat(ascendants.get(0).id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("les filles forment un arbre, en largeur d'abord")
        void arbreDesFilles() {
            Reine depart = reine(1L, null);
            Reine fille = reine(2L, depart);
            Reine autreFille = reine(3L, depart);
            Reine petiteFille = reine(4L, fille);
            when(reines.findById(1L)).thenReturn(Optional.of(depart));
            when(reines.findByMere_IdOrderByIdAsc(1L)).thenReturn(List.of(fille, autreFille));
            when(reines.findByMere_IdOrderByIdAsc(2L)).thenReturn(List.of(petiteFille));

            List<Genealogie.Noeud> descendants = service.genealogie(1L).descendants();

            // Les filles avant les petites-filles : la liste est directement
            // dessinable de haut en bas, sans être retriée par l'interface.
            assertThat(descendants).extracting(Genealogie.Noeud::id)
                    .containsExactly(2L, 3L, 4L);
            assertThat(descendants).extracting(Genealogie.Noeud::profondeur)
                    .containsExactly(1, 1, 2);
            assertThat(descendants.get(2).parentId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("une fille déjà vue n'est pas ajoutée deux fois")
        void filleDejaVue() {
            Reine depart = reine(1L, null);
            Reine fille = reine(2L, depart);
            when(reines.findById(1L)).thenReturn(Optional.of(depart));
            when(reines.findByMere_IdOrderByIdAsc(1L)).thenReturn(List.of(fille));
            // La fille se déclare mère d'elle-même dans une base reprise.
            when(reines.findByMere_IdOrderByIdAsc(2L)).thenReturn(List.of(fille, depart));

            assertThat(service.genealogie(1L).descendants())
                    .extracting(Genealogie.Noeud::id).containsExactly(2L);
        }

        @Test
        @DisplayName("une reine inconnue est refusée en 404")
        void reineIntrouvable() {
            when(reines.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.genealogie(999L))
                    .isInstanceOf(RessourceIntrouvable.class);
        }
    }

    // ── Rattachements ───────────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche ou une série inconnue dans ce tenant est refusée")
    void rattachementsInconnus() {
        when(ruches.findById(99L)).thenReturn(Optional.empty());
        ReineElevageCorps avecRuche = new ReineElevageCorps(null, null, 99L, null, null,
                "elevage", null, null, null, null, null, null, null, null, null, null,
                null, null);

        assertThatThrownBy(() -> service.creer(avecRuche))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("une série inconnue est refusée, avec son identifiant")
    void serieInconnue() {
        when(series.findById(77L)).thenReturn(Optional.empty());
        ReineElevageCorps avecSerie = new ReineElevageCorps(null, null, null, 77L, null,
                "elevage", null, null, null, null, null, null, null, null, null, null,
                null, null);

        assertThatThrownBy(() -> service.creer(avecSerie))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("77");
    }

    // ── Lecture et suppression ──────────────────────────────────────────────

    @Test
    @DisplayName("la liste suit l'ordre du dépôt, la plus récente d'abord")
    void liste() {
        Reine une = reine(1L, null);
        when(reines.findAllByOrderByIdDesc()).thenReturn(List.of(une));

        assertThat(service.lister()).hasSize(1);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt, et refuse une reine inconnue")
    void suppression() {
        Reine existante = reine(1L, null);
        when(reines.findById(1L)).thenReturn(Optional.of(existante));
        when(reines.findById(999L)).thenReturn(Optional.empty());

        service.supprimer(1L);

        verify(reines).delete(existante);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("l'index génétique passe par la reine, mais se calcule ailleurs")
    void indexDelegue() {
        Reine existante = reine(1L, null);
        when(reines.findById(1L)).thenReturn(Optional.of(existante));

        service.index(1L);

        // La porte d'entrée est la reine ; le calcul lit quatre registres que ce
        // service n'a aucune raison de connaître.
        verify(index).pour(existante);
    }

    @Test
    @DisplayName("obtenir rend la reine demandée")
    void obtenir() {
        Reine existante = reine(1L, null);
        when(reines.findById(1L)).thenReturn(Optional.of(existante));

        ReineElevage reponse = service.obtenir(1L);

        assertThat(reponse.id()).isEqualTo(1L);
    }

    // ── Séries ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("une série inconnue est refusée en 404 à la suppression")
    void serieIntrouvable() {
        when(series.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.supprimerSerie(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("la liste des séries passe par le dépôt")
    void listeDesSeries() {
        SerieElevage serie = mock(SerieElevage.class);
        when(series.findAllByOrderByDateGreffageDescIdDesc()).thenReturn(List.of(serie));

        assertThat(service.listerSeries()).hasSize(1);
    }
}
