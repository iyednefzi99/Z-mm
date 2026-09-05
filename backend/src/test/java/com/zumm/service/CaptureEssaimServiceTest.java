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
import com.zumm.domain.CaptureEssaim;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.CaptureEssaimRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.CaptureEssaimCorps;
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

/**
 * Captures d'essaim (SPRINT-21, lot 3 du plan de couverture).
 *
 * <p>55,1 % d'instructions et 40 % de branches. Deux décisions du SPRINT-21 s'y
 * jouent, et la première explique la forme entière de l'API :
 *
 * <ol>
 *   <li><strong>Loger est une opération à part.</strong> On capture un jour, on
 *       loge quand la colonie a pris. Forcer la ruche à la saisie initiale
 *       reviendrait à n'enregistrer que les captures <em>réussies</em> —
 *       c'est-à-dire à perdre la seule statistique qui ait de la valeur.</li>
 *   <li><strong>L'origine de la ruche est alignée sur l'événement qui
 *       l'explique</strong> ({@code essaim_capture}), et seulement quand elle
 *       n'est pas déjà renseignée : deux endroits qui disent la même chose ne
 *       doivent pas pouvoir la dire différemment.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CaptureEssaimServiceTest {

    @Mock private CaptureEssaimRepository captures;
    @Mock private AgentRepository agents;
    @Mock private RucheRepository ruches;
    @Mock private SiteRepository sites;

    private CaptureEssaimService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 5, 12);

    @BeforeEach
    void monter() {
        service = new CaptureEssaimService(captures, agents, ruches, sites);
        lenient().when(captures.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Agent agent() {
        Agent a = mock(Agent.class);
        lenient().when(a.getId()).thenReturn(3L);
        lenient().when(a.getNom()).thenReturn("Amal");
        return a;
    }

    private static Ruche ruche(Long id, String origine, Site site) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getOrigine()).thenReturn(origine);
        lenient().when(r.getSite()).thenReturn(site);
        return r;
    }

    /**
     * Une capture simulee, munie de ce que {@code CaptureEssaimReponse.de} lit.
     *
     * <p>Un mock nu rendrait {@code null} sur {@code getAgent()} et la
     * projection tomberait en NullPointer — un echec de montage, pas un echec
     * de comportement.
     */
    private static CaptureEssaim capture() {
        // L'agent se construit AVANT : `agent()` fabrique lui-meme un mock, et
        // l'appeler dans un `thenReturn` laisserait ce stub-ci inacheve.
        Agent a = agent();
        CaptureEssaim c = mock(CaptureEssaim.class);
        lenient().when(c.getAgent()).thenReturn(a);
        lenient().when(c.getDateCapture()).thenReturn(JOUR);
        lenient().when(c.getOrigine()).thenReturn("essaim_naturel");
        return c;
    }

    private static CaptureEssaimCorps corps(Long rucheId, Long siteId) {
        return new CaptureEssaimCorps(3L, rucheId, siteId, JOUR, "essaim_naturel",
                "Haie du chemin bas", new BigDecimal("1.8"), new BigDecimal("3.5"), null);
    }

    // ── Saisie ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("une capture s'enregistre sans ruche : c'est le cas normal")
    void captureSansRuche() {
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));

        service.enregistrer(corps(null, null));

        // On capture un jour, on loge quand la colonie a pris. Exiger la ruche
        // ici ne laisserait entrer que les captures réussies.
        verify(captures).save(any(CaptureEssaim.class));
        verify(ruches, never()).findById(any());
        verify(sites, never()).findById(any());
    }

    @Test
    @DisplayName("un agent inconnu dans ce tenant est refusé avant tout le reste")
    void agentInconnu() {
        when(agents.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(corps(null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("3");
        verify(captures, never()).save(any());
    }

    @Test
    @DisplayName("un rucher inconnu est refusé, avec son identifiant")
    void siteInconnu() {
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));
        when(sites.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(corps(null, 9L)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("une ruche inconnue est refusée, avec son identifiant")
    void rucheInconnue() {
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));
        when(ruches.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(corps(42L, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("42");
    }

    // ── L'origine alignée ───────────────────────────────────────────────────

    @Test
    @DisplayName("loger pose l'origine « essaim_capture » sur une ruche qui n'en a pas")
    void originePoseeQuandAbsente() {
        CaptureEssaim capture = capture();
        Ruche accueil = ruche(42L, null, null);
        when(captures.findById(1L)).thenReturn(Optional.of(capture));
        when(ruches.findById(42L)).thenReturn(Optional.of(accueil));
        when(capture.getRuche()).thenReturn(accueil);

        service.loger(1L, 42L);

        // La filiation et l'origine disent la même chose : les laisser diverger
        // ferait mentir la statistique par origine.
        verify(accueil).setOrigine("essaim_capture");
        verify(capture).setRuche(accueil);
    }

    @Test
    @DisplayName("une origine déjà renseignée n'est jamais écrasée")
    void origineDejaRenseignee() {
        CaptureEssaim capture = capture();
        Ruche accueil = ruche(42L, "achat", null);
        when(captures.findById(1L)).thenReturn(Optional.of(capture));
        when(ruches.findById(42L)).thenReturn(Optional.of(accueil));
        when(capture.getRuche()).thenReturn(accueil);

        service.loger(1L, 42L);

        // L'apiculteur a peut-être une raison de dire « achat » ; le logiciel
        // n'a pas à la corriger.
        verify(accueil, never()).setOrigine(any());
    }

    @Test
    @DisplayName("loger recopie le rucher de la ruche d'accueil, quand elle en a un")
    void sitePropageDepuisLaRuche() {
        CaptureEssaim capture = capture();
        Site site = mock(Site.class);
        Ruche accueil = ruche(42L, null, site);
        when(captures.findById(1L)).thenReturn(Optional.of(capture));
        when(ruches.findById(42L)).thenReturn(Optional.of(accueil));
        when(capture.getRuche()).thenReturn(accueil);

        service.loger(1L, 42L);

        // La capture rejoint le rucher où sa ruche se trouve : sans cela, elle
        // resterait rattachée au lieu de capture, qui n'est pas un rucher.
        verify(capture).setSite(site);
    }

    @Test
    @DisplayName("une ruche sans rucher ne fabrique pas de rattachement")
    void rucheSansRucher() {
        CaptureEssaim capture = capture();
        Ruche accueil = ruche(42L, null, null);
        when(captures.findById(1L)).thenReturn(Optional.of(capture));
        when(ruches.findById(42L)).thenReturn(Optional.of(accueil));
        when(capture.getRuche()).thenReturn(accueil);

        service.loger(1L, 42L);

        verify(capture, never()).setSite(any());
    }

    @Test
    @DisplayName("loger une capture inconnue est refusé en 404")
    void captureIntrouvable() {
        when(captures.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loger(999L, 42L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("une période inversée est refusée, avec les deux dates")
    void periodeInversee() {
        assertThatThrownBy(() -> service.saison(JOUR, JOUR.minusDays(1)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("precede son debut");
    }

    @Test
    @DisplayName("une période d'un seul jour est valide")
    void periodeDUnJour() {
        when(captures.findByDateCaptureBetweenOrderByDateCaptureDescIdDesc(JOUR, JOUR))
                .thenReturn(List.of());

        assertThat(service.saison(JOUR, JOUR)).isEmpty();
    }

    @Test
    @DisplayName("les captures en attente sont celles qui n'ont pas encore de ruche")
    void enAttente() {
        CaptureEssaim capture = capture();
        when(captures.findByRucheIsNullOrderByDateCaptureDescIdDesc())
                .thenReturn(List.of(capture));

        // « Ce qu'il reste à loger » est la question qu'on se pose au printemps,
        // et elle se lit sur l'absence de ruche, pas sur un état à maintenir.
        assertThat(service.enAttente()).hasSize(1);
    }

    @Test
    @DisplayName("la liste suit l'ordre du dépôt, la plus récente d'abord")
    void liste() {
        CaptureEssaim capture = capture();
        when(captures.findAllByOrderByDateCaptureDescIdDesc()).thenReturn(List.of(capture));

        assertThat(service.lister()).hasSize(1);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt, et refuse une capture inconnue")
    void suppression() {
        CaptureEssaim capture = capture();
        when(captures.findById(1L)).thenReturn(Optional.of(capture));
        when(captures.findById(999L)).thenReturn(Optional.empty());

        service.supprimer(1L);

        verify(captures).delete(capture);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }
}
