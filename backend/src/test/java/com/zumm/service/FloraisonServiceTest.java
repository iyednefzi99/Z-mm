package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.FloraisonObservee;
import com.zumm.domain.RessourceFlorale;
import com.zumm.domain.Site;
import com.zumm.repository.FloraisonObserveeRepository;
import com.zumm.repository.RessourceFloraleRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.FloraisonCorps;
import com.zumm.web.dto.FloraisonReponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Calendrier de floraison observée (SPRINT-32, lot 3 du plan de couverture).
 *
 * <p>71,8 % d'instructions et <strong>55,6 % de branches</strong>, le plus
 * mauvais ratio restant du paquet. L'écart porte entièrement sur deux règles,
 * et la première explique pourquoi ce service existe :
 *
 * <ol>
 *   <li><strong>Une ressource ne fleurit qu'une fois par an</strong>, et la base
 *       le garantit. Ce service <em>complète</em> l'observation existante au lieu
 *       d'échouer : l'apiculteur note le début en avril et le pic en mai —
 *       refuser la seconde saisie lui ferait perdre la première.</li>
 *   <li><strong>Une floraison ne finit pas avant d'avoir commencé.</strong> La
 *       base le refuse aussi ({@code ck_floraison_ordre}), mais en 409 : deux
 *       dates inversées sont une <em>faute de frappe</em>, pas un conflit
 *       d'état, et méritent un 400 qui nomme la date fautive.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class FloraisonServiceTest {

    @Mock private FloraisonObserveeRepository floraisons;
    @Mock private RessourceFloraleRepository ressources;

    private FloraisonService service;

    private static final LocalDate DEBUT = LocalDate.of(2026, 4, 18);

    @BeforeEach
    void monter() {
        service = new FloraisonService(floraisons, ressources);
        lenient().when(floraisons.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(floraisons.findByRessource_IdAndAnnee(any(), any()))
                .thenReturn(Optional.empty());
    }

    private static RessourceFlorale ressource() {
        Site s = mock(Site.class);
        RessourceFlorale r = mock(RessourceFlorale.class);
        lenient().when(s.getId()).thenReturn(1L);
        lenient().when(r.getId()).thenReturn(3L);
        lenient().when(r.getRessource()).thenReturn("colza");
        lenient().when(r.getMoisDebut()).thenReturn(4);
        lenient().when(r.getSite()).thenReturn(s);
        return r;
    }

    private static FloraisonCorps corps(LocalDate debut, LocalDate pic, LocalDate fin) {
        return new FloraisonCorps(3L, 2026, debut, pic, fin, 2, null);
    }

    // ── L'ordre des dates ───────────────────────────────────────────────────

    @Test
    @DisplayName("un pic antérieur au début est refusé en 400 qui le nomme")
    void picAvantDebut() {
        RessourceFlorale r = ressource();
        when(ressources.findById(3L)).thenReturn(Optional.of(r));

        // La base le refuserait aussi, mais en 409 : deux dates inversées sont
        // une faute de frappe, pas un conflit d'état.
        assertThatThrownBy(() -> service.enregistrer(corps(DEBUT, DEBUT.minusDays(1), null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("pic de floraison precede son debut");
        verify(floraisons, never()).save(any());
    }

    @Test
    @DisplayName("une fin antérieure au début est refusée")
    void finAvantDebut() {
        RessourceFlorale r = ressource();
        when(ressources.findById(3L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.enregistrer(corps(DEBUT, null, DEBUT.minusDays(1))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("fin de floraison precede son debut");
    }

    @Test
    @DisplayName("une fin antérieure au pic est refusée, même si elle suit le début")
    void finAvantPic() {
        RessourceFlorale r = ressource();
        when(ressources.findById(3L)).thenReturn(Optional.of(r));

        // Les trois dates forment une suite ; vérifier seulement les extrémités
        // laisserait passer « début 18 avril, pic 2 mai, fin 25 avril ».
        assertThatThrownBy(() -> service.enregistrer(
                corps(DEBUT, DEBUT.plusDays(14), DEBUT.plusDays(7))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("fin de floraison precede son pic");
    }

    @Test
    @DisplayName("trois dates dans l'ordre passent, et les dates égales aussi")
    void ordreValide() {
        RessourceFlorale r = ressource();
        when(ressources.findById(3L)).thenReturn(Optional.of(r));

        // Une floraison d'un seul jour est une floraison : la comparaison est
        // stricte, pas exclusive.
        assertThat(service.enregistrer(corps(DEBUT, DEBUT.plusDays(14), DEBUT.plusDays(30))))
                .isNotNull();
        assertThat(service.enregistrer(corps(DEBUT, DEBUT, DEBUT))).isNotNull();
    }

    @Test
    @DisplayName("un début seul suffit : le pic et la fin viennent plus tard")
    void debutSeul() {
        RessourceFlorale r = ressource();
        when(ressources.findById(3L)).thenReturn(Optional.of(r));

        assertThat(service.enregistrer(corps(DEBUT, null, null))).isNotNull();
    }

    // ── Compléter plutôt qu'échouer ─────────────────────────────────────────

    @Test
    @DisplayName("une seconde saisie COMPLÈTE l'observation de l'année")
    void secondeSaisieComplete() {
        RessourceFlorale r = ressource();
        FloraisonObservee existante = new FloraisonObservee(r, 2026, DEBUT);
        when(ressources.findById(3L)).thenReturn(Optional.of(r));
        when(floraisons.findByRessource_IdAndAnnee(3L, 2026))
                .thenReturn(Optional.of(existante));

        service.enregistrer(corps(DEBUT, DEBUT.plusDays(14), null));

        // L'apiculteur note le début en avril et le pic en mai. Refuser la
        // seconde saisie lui ferait perdre la première ; en créer une seconde
        // heurterait `uq_floraison_annee`.
        assertThat(existante.getDatePic()).isEqualTo(DEBUT.plusDays(14));
        assertThat(existante.getDateDebut()).isEqualTo(DEBUT);
        verify(floraisons).save(existante);
    }

    @Test
    @DisplayName("la seconde saisie peut aussi corriger le début")
    void correctionDuDebut() {
        RessourceFlorale r = ressource();
        FloraisonObservee existante = new FloraisonObservee(r, 2026, DEBUT);
        when(ressources.findById(3L)).thenReturn(Optional.of(r));
        when(floraisons.findByRessource_IdAndAnnee(3L, 2026))
                .thenReturn(Optional.of(existante));

        service.enregistrer(corps(DEBUT.minusDays(3), null, null));

        assertThat(existante.getDateDebut()).isEqualTo(DEBUT.minusDays(3));
    }

    @Test
    @DisplayName("une abondance absente reste nulle, elle ne devient pas zéro")
    void abondanceAbsente() {
        RessourceFlorale r = ressource();
        when(ressources.findById(3L)).thenReturn(Optional.of(r));
        FloraisonCorps sansAbondance =
                new FloraisonCorps(3L, 2026, DEBUT, null, null, null, null);

        FloraisonReponse reponse = service.enregistrer(sansAbondance);

        // Zéro veut dire « aucune floraison observée » ; l'absence veut dire
        // « pas noté ». Les confondre ferait entrer une observation nulle dans
        // une moyenne qui ne la mérite pas.
        assertThat(reponse.abondance()).isNull();
    }

    @Test
    @DisplayName("une abondance nulle est conservée telle quelle")
    void abondanceZero() {
        RessourceFlorale r = ressource();
        when(ressources.findById(3L)).thenReturn(Optional.of(r));
        FloraisonCorps nulle = new FloraisonCorps(3L, 2026, DEBUT, null, null, 0, null);

        assertThat(service.enregistrer(nulle).abondance()).isZero();
    }

    // ── Rattachement ────────────────────────────────────────────────────────

    @Test
    @DisplayName("une ressource inconnue dans ce tenant est refusée avant tout contrôle")
    void ressourceInconnue() {
        when(ressources.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(corps(DEBUT, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("3");
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sans rucher, toutes les floraisons ; avec, celles du rucher")
    void listeAvecEtSansSite() {
        RessourceFlorale r = ressource();
        FloraisonObservee f = new FloraisonObservee(r, 2026, DEBUT);
        when(floraisons.findAllByOrderByAnneeDescDateDebutAsc()).thenReturn(List.of(f));
        when(floraisons.findByRessource_Site_IdOrderByAnneeDescDateDebutAsc(1L))
                .thenReturn(List.of());

        // Un rucher absent n'est pas un rucher vide : rendre zéro résultat
        // ferait croire qu'aucune floraison n'a été notée nulle part.
        assertThat(service.lister(null)).hasSize(1);
        assertThat(service.lister(1L)).isEmpty();
    }

    @Test
    @DisplayName("une floraison inconnue est refusée en 404")
    void floraisonIntrouvable() {
        when(floraisons.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt")
    void suppression() {
        RessourceFlorale r = ressource();
        FloraisonObservee f = new FloraisonObservee(r, 2026, DEBUT);
        when(floraisons.findById(1L)).thenReturn(Optional.of(f));

        service.supprimer(1L);

        verify(floraisons).delete(f);
    }
}
