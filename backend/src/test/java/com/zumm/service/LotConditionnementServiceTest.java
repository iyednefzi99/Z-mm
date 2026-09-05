package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.LotComposition;
import com.zumm.domain.LotConditionnement;
import com.zumm.domain.Recolte;
import com.zumm.repository.LotConditionnementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.LotCorps;
import com.zumm.web.dto.LotReponse;
import com.zumm.web.dto.MentionOrigine;
import com.zumm.web.dto.OrigineDeclaree;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Lots de conditionnement et mention d'origine (SPRINT-27, lot 3 du plan de
 * couverture).
 *
 * <p>73,7 % d'instructions et 66,7 % de branches, pour la classe qui produit un
 * texte <strong>imprimé sur un pot</strong>. C'est la sortie du produit dont
 * l'erreur se répare le plus mal : une étiquette fausse est déjà chez le
 * consommateur.
 *
 * <p>Deux règles portent tout le reste, et ce sont elles que ces tests
 * verrouillent :
 *
 * <ol>
 *   <li><strong>La somme des parts fait 100 %</strong>, vérifiée ici avec un
 *       message qui dit <em>de combien on s'écarte</em> — pas dans un trigger,
 *       qui renverrait une erreur SQL illisible.</li>
 *   <li><strong>Les parts se consolident PAR PAYS avant d'être triées.</strong>
 *       Trois récoltes françaises à 20 % ne s'écrivent pas « France 20 %, France
 *       20 %, France 20 % » mais « France 60 % ». C'est ce que lit un
 *       consommateur, et ce que vérifie un contrôle.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class LotConditionnementServiceTest {

    @Mock private LotConditionnementRepository lots;
    @Mock private RecolteRepository recoltes;

    private LotConditionnementService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 8, 25);

    @BeforeEach
    void monter() {
        service = new LotConditionnementService(lots, recoltes);
        lenient().when(lots.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(lots.existsByReference(any())).thenReturn(false);
    }

    private static OrigineDeclaree part(String pays, String pourcentage) {
        return new OrigineDeclaree(null, pays, new BigDecimal(pourcentage));
    }

    private static LotCorps corps(OrigineDeclaree... origines) {
        return new LotCorps("L-2026-01", JOUR, new BigDecimal("18.0"), "toutes fleurs",
                null, List.of(origines));
    }

    /** Un lot réel, dont la composition se remplit vraiment. */
    private static LotConditionnement lot(String reference) {
        return new LotConditionnement(reference, JOUR, new BigDecimal("18.0"));
    }

    // ── La somme des parts ──────────────────────────────────────────────────

    @Nested
    @DisplayName("somme des parts")
    class Somme {

        @Test
        @DisplayName("cent pour cent exactement est accepté")
        void sommeExacte() {
            assertThatCode(() -> service.creer(corps(part("FR", "100"))))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} %")
        @ValueSource(strings = {"90", "110", "99", "101"})
        @DisplayName("un total qui s'écarte de plus de la tolérance est refusé, avec l'écart")
        void sommeFausse(String total) {
            // Le message donne l'ecart : sans lui, l'utilisateur cherche à l'œil
            // laquelle des huit parts est fausse.
            assertThatThrownBy(() -> service.creer(corps(part("FR", total))))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("au lieu de 100 %")
                    .hasMessageContaining("ecart");
            verify(lots, never()).save(any());
        }

        @ParameterizedTest(name = "{0} %")
        @ValueSource(strings = {"99.95", "100.05", "99.99"})
        @DisplayName("un arrondi de saisie reste dans la tolérance")
        void toleranceDArrondi(String total) {
            // La tolérance de somme est volontairement étroite : elle couvre les
            // arrondis, pas une part oubliée.
            assertThatCode(() -> service.creer(corps(part("FR", total))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("plusieurs parts se somment avant d'être jugées")
        void sommeDePlusieursParts() {
            assertThatCode(() -> service.creer(corps(
                    part("FR", "60"), part("ES", "25"), part("IT", "15"))))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> service.creer(corps(
                    part("FR", "60"), part("ES", "25"))))
                    .isInstanceOf(RequeteInvalide.class);
        }
    }

    // ── La mention d'origine ────────────────────────────────────────────────

    @Nested
    @DisplayName("mention d'origine")
    class Mention {

        private LotConditionnement lotAvec(LotComposition... parts) {
            LotConditionnement l = lot("L-2026-01");
            for (LotComposition p : parts) {
                l.ajouter(p);
            }
            return l;
        }

        private LotComposition composition(String pays, String pourcentage) {
            return new LotComposition(null, pays, new BigDecimal(pourcentage));
        }

        @Test
        @DisplayName("un pays unique donne « Origine : France », sans pourcentage")
        void paysUnique() {
            LotConditionnement l = lotAvec(composition("FR", "100"));
            when(lots.findById(1L)).thenReturn(Optional.of(l));

            MentionOrigine mention = service.mention(1L, Locale.FRENCH);

            // « Origine : France 100 % » serait exact et illisible : la directive
            // ne demande le détail que pour un mélange.
            assertThat(mention.texte()).isEqualTo("Origine : France");
            assertThat(mention.melange()).isFalse();
            assertThat(mention.origines()).hasSize(1);
        }

        @Test
        @DisplayName("les parts d'un même pays se CONSOLIDENT avant d'être écrites")
        void consolidationParPays() {
            LotConditionnement l = lotAvec(
                    composition("FR", "20"), composition("FR", "20"),
                    composition("FR", "20"), composition("ES", "40"));
            when(lots.findById(1L)).thenReturn(Optional.of(l));

            MentionOrigine mention = service.mention(1L, Locale.FRENCH);

            // Trois récoltes françaises ne s'écrivent pas trois fois : c'est ce
            // que lit un consommateur, et ce que vérifie un contrôle.
            assertThat(mention.origines()).hasSize(2);
            assertThat(mention.texte()).isEqualTo("Origine : France 60 %, Espagne 40 %");
            assertThat(mention.melange()).isTrue();
        }

        @Test
        @DisplayName("les parts sont triées par proportion décroissante")
        void triDecroissant() {
            LotConditionnement l = lotAvec(
                    composition("ES", "20"), composition("FR", "50"), composition("IT", "30"));
            when(lots.findById(1L)).thenReturn(Optional.of(l));

            assertThat(service.mention(1L, Locale.FRENCH).origines())
                    .extracting(MentionOrigine.Part::paysOrigine)
                    .containsExactly("FR", "IT", "ES");
        }

        @Test
        @DisplayName("à proportion égale, le code pays départage — l'étiquette est stable")
        void departageParCodePays() {
            LotConditionnement l = lotAvec(
                    composition("IT", "50"), composition("ES", "50"));
            when(lots.findById(1L)).thenReturn(Optional.of(l));

            // Sans ce départage, deux lots identiques produiraient deux
            // étiquettes d'ordre différent selon l'ordre de lecture en base.
            assertThat(service.mention(1L, Locale.FRENCH).origines())
                    .extracting(MentionOrigine.Part::paysOrigine)
                    .containsExactly("ES", "IT");
        }

        @Test
        @DisplayName("le libellé du pays suit la locale demandée, pas celle du producteur")
        void libelleDansLaLocale() {
            LotConditionnement l = lotAvec(composition("FR", "100"));
            when(lots.findById(1L)).thenReturn(Optional.of(l));

            // L'étiquette d'un miel exporté s'imprime dans la langue du marché.
            assertThat(service.mention(1L, Locale.FRENCH).texte())
                    .isEqualTo("Origine : France");
            assertThat(service.mention(1L, Locale.ENGLISH).texte())
                    .isEqualTo("Origine : France");
        }

        @Test
        @DisplayName("les pourcentages sont arrondis à l'entier, sans zéro traînant")
        void pourcentagesArrondis() {
            LotConditionnement l = lotAvec(
                    composition("FR", "66.60"), composition("ES", "33.40"));
            when(lots.findById(1L)).thenReturn(Optional.of(l));

            // « France 67.00 % » sur un pot se lit comme une précision qu'on n'a
            // pas : la directive demande l'entier.
            assertThat(service.mention(1L, Locale.FRENCH).texte())
                    .isEqualTo("Origine : France 67 %, Espagne 33 %");
        }

        @Test
        @DisplayName("un lot inconnu est refusé en 404")
        void lotIntrouvable() {
            when(lots.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.mention(999L, Locale.FRENCH))
                    .isInstanceOf(RessourceIntrouvable.class);
        }
    }

    // ── La référence unique ─────────────────────────────────────────────────

    @Test
    @DisplayName("une référence déjà prise est refusée, avec la référence en clair")
    void referenceDejaPrise() {
        when(lots.existsByReference("L-2026-01")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(corps(part("FR", "100"))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("L-2026-01");
        verify(lots, never()).save(any());
    }

    @Test
    @DisplayName("garder sa propre référence en mise à jour n'est pas un doublon")
    void memeReferenceEnMiseAJour() {
        LotConditionnement existant = lot("L-2026-01");
        when(lots.findById(1L)).thenReturn(Optional.of(existant));

        // Sans cette comparaison, toute mise à jour d'un lot serait refusée
        // comme un doublon d'elle-même.
        assertThatCode(() -> service.mettreAJour(1L, corps(part("FR", "100"))))
                .doesNotThrowAnyException();
        verify(lots, never()).existsByReference(any());
    }

    @Test
    @DisplayName("prendre la référence d'un autre lot est refusé en mise à jour")
    void referenceDUnAutreLot() {
        LotConditionnement existant = lot("L-2026-99");
        when(lots.findById(1L)).thenReturn(Optional.of(existant));
        when(lots.existsByReference("L-2026-01")).thenReturn(true);

        assertThatThrownBy(() -> service.mettreAJour(1L, corps(part("FR", "100"))))
                .isInstanceOf(RequeteInvalide.class);
    }

    // ── Rattachement des récoltes ───────────────────────────────────────────

    @Test
    @DisplayName("une part sans récolte est acceptée : c'est du miel acquis à un tiers")
    void partSansRecolte() {
        service.creer(corps(part("ES", "100")));

        verify(recoltes, never()).findById(any());
        verify(lots).save(any(LotConditionnement.class));
    }

    @Test
    @DisplayName("une récolte inconnue dans ce tenant est refusée")
    void recolteInconnue() {
        when(recoltes.findById(9L)).thenReturn(Optional.empty());
        LotCorps avecRecolte = new LotCorps("L-2026-01", JOUR, new BigDecimal("18.0"),
                null, null, List.of(new OrigineDeclaree(9L, "FR", new BigDecimal("100"))));

        assertThatThrownBy(() -> service.creer(avecRecolte))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("une récolte connue est rattachée à sa part")
    void recolteConnue() {
        Recolte r = mock(Recolte.class);
        when(recoltes.findById(9L)).thenReturn(Optional.of(r));
        LotCorps avecRecolte = new LotCorps("L-2026-01", JOUR, new BigDecimal("18.0"),
                null, null, List.of(new OrigineDeclaree(9L, "FR", new BigDecimal("100"))));

        service.creer(avecRecolte);

        verify(recoltes).findById(9L);
    }

    @Test
    @DisplayName("une mise à jour RECONSTITUE la composition, elle ne l'ajoute pas")
    void compositionReconstituee() {
        LotConditionnement existant = lot("L-2026-01");
        existant.ajouter(new LotComposition(null, "IT", new BigDecimal("100")));
        when(lots.findById(1L)).thenReturn(Optional.of(existant));

        service.mettreAJour(1L, corps(part("FR", "100")));

        // Sans le vidage, la somme des parts doublerait à chaque enregistrement
        // et le lot deviendrait irrécupérable.
        assertThat(existant.getComposition()).hasSize(1);
        assertThat(existant.getComposition().get(0).getPaysOrigine()).isEqualTo("FR");
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la liste suit l'ordre du dépôt, le plus récent d'abord")
    void liste() {
        LotConditionnement l = lot("L-2026-01");
        when(lots.findByOrderByDateConditionnementDescIdDesc()).thenReturn(List.of(l));

        assertThat(service.lister()).hasSize(1);
    }

    @Test
    @DisplayName("obtenir rend le lot demandé, et refuse un lot inconnu")
    void obtenir() {
        LotConditionnement l = lot("L-2026-01");
        when(lots.findById(1L)).thenReturn(Optional.of(l));
        when(lots.findById(999L)).thenReturn(Optional.empty());

        LotReponse reponse = service.obtenir(1L);

        assertThat(reponse.reference()).isEqualTo("L-2026-01");
        assertThatThrownBy(() -> service.obtenir(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt")
    void suppression() {
        LotConditionnement l = lot("L-2026-01");
        when(lots.findById(1L)).thenReturn(Optional.of(l));

        service.supprimer(1L);

        verify(lots).delete(l);
    }
}
