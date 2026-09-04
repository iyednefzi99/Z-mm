package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.AuditEntree;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Traitement;
import com.zumm.repository.AuditEntreeRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.web.RegleMetierViolee;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.RecolteCorps;
import com.zumm.web.dto.RecolteReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests unitaires de la génération du lot et du QR (US-033, US-036). */
@ExtendWith(MockitoExtension.class)
class RecolteServiceTest {

    @Mock private RecolteRepository recoltes;
    @Mock private RucheRepository ruches;
    @Mock private TraitementRepository traitements;
    @Mock private AuditEntreeRepository audits;

    private RecolteService service() {
        return new RecolteService(recoltes, ruches, traitements, audits,
                new OperationsLotService(ruches));
    }

    /** Corps ordinaire : pas de forcage, pas de motif. */
    private RecolteCorps corps(long rucheId, LocalDate date, BigDecimal kilos, String typeMiel) {
        return new RecolteCorps(rucheId, date, kilos, typeMiel, null, null, null, false, null);
    }

    @Test
    @DisplayName("génère un lot lisible ZUMM-<ruche>-<jour>-<séquence> et son QR payload")
    void genereLotEtQr() {
        Ruche ruche = mock(Ruche.class);
        when(ruche.getId()).thenReturn(5L);
        when(ruche.getModele()).thenReturn("Dadant");
        LocalDate date = LocalDate.of(2026, 7, 15);
        when(ruches.findById(5L)).thenReturn(Optional.of(ruche));
        when(recoltes.countByRuche_IdAndDateRecolte(eq(5L), eq(date))).thenReturn(0L);
        when(recoltes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecolteReponse r = service().creer(corps(5L, date, new BigDecimal("18.500"), "Toutes fleurs"));

        assertThat(r.lot()).isEqualTo("ZUMM-5-20260715-01");
        assertThat(r.qrPayload()).isEqualTo("zumm:tracabilite:ZUMM-5-20260715-01");
        assertThat(r.rucheModele()).isEqualTo("Dadant");
    }

    @Test
    @DisplayName("incrémente la séquence du lot pour une 2ᵉ récolte le même jour")
    void incrementeSequence() {
        Ruche ruche = mock(Ruche.class);
        when(ruche.getId()).thenReturn(5L);
        when(ruche.getModele()).thenReturn("Dadant");
        LocalDate date = LocalDate.of(2026, 7, 15);
        when(ruches.findById(5L)).thenReturn(Optional.of(ruche));
        when(recoltes.countByRuche_IdAndDateRecolte(eq(5L), eq(date))).thenReturn(1L);
        when(recoltes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecolteReponse r = service().creer(corps(5L, date, BigDecimal.ONE, null));

        assertThat(r.lot()).isEqualTo("ZUMM-5-20260715-02");
    }

    // ─── Carence opposable (SPRINT-22, lot A) ────────────────────────────────

    /**
     * Un traitement sous carence, monte en stubs LAXISTES : selon le cas teste, le
     * service s'arrete au filtre par ruche et ne lira ni le produit ni la date de
     * retrait. Des stubs stricts feraient alors echouer un test qui passe, pour
     * une raison qui n'a rien a voir avec ce qu'il verifie.
     */
    private Traitement carence(LocalDate retrait, long rucheId) {
        Ruche ruche = mock(Ruche.class);
        org.mockito.Mockito.lenient().when(ruche.getId()).thenReturn(rucheId);
        Traitement t = mock(Traitement.class);
        org.mockito.Mockito.lenient().when(t.getRuche()).thenReturn(ruche);
        org.mockito.Mockito.lenient().when(t.sousCarence(any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(t.getDateRetrait()).thenReturn(retrait);
        org.mockito.Mockito.lenient().when(t.getProduit()).thenReturn("Apivar");
        return t;
    }

    private Ruche rucheRecoltee(LocalDate date) {
        Ruche ruche = mock(Ruche.class);
        org.mockito.Mockito.lenient().when(ruche.getId()).thenReturn(5L);
        when(ruches.findById(5L)).thenReturn(Optional.of(ruche));
        return ruche;
    }

    @Test
    @DisplayName("refuse la recolte d'une ruche sous carence, en disant jusqu'a quand")
    void refuseSousCarence() {
        LocalDate date = LocalDate.of(2026, 7, 15);
        rucheRecoltee(date);
        // Le traitement se construit AVANT le `when` : l'imbriquer produirait un
        // stubbing inacheve, Mockito n'admettant pas qu'on stube pendant qu'on stube.
        Traitement bloquant = carence(LocalDate.of(2026, 7, 25), 5L);
        when(traitements.sousCarenceAu(date)).thenReturn(List.of(bloquant));

        assertThatThrownBy(() -> service().creer(corps(5L, date, BigDecimal.ONE, null)))
                // 409 et non 400 : la requete est valide, c'est l'etat qui s'y oppose.
                .isInstanceOf(RegleMetierViolee.class)
                // Un refus qui ne dit pas « jusqu'a quand » se contourne : il faut
                // que l'apiculteur puisse reporter, pas seulement echouer.
                .hasMessageContaining("2026-07-25")
                .hasMessageContaining("Apivar");
    }

    @Test
    @DisplayName("un forcage sans motif est refuse : une case cochee ne vaut rien")
    void forcageMuetRefuse() {
        LocalDate date = LocalDate.of(2026, 7, 15);
        rucheRecoltee(date);
        // Le traitement se construit AVANT le `when` : l'imbriquer produirait un
        // stubbing inacheve, Mockito n'admettant pas qu'on stube pendant qu'on stube.
        Traitement bloquant = carence(LocalDate.of(2026, 7, 25), 5L);
        when(traitements.sousCarenceAu(date)).thenReturn(List.of(bloquant));

        assertThatThrownBy(() -> service().creer(
                new RecolteCorps(5L, date, BigDecimal.ONE, null, null, null, null, true, "   ")))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("motif");
    }

    @Test
    @DisplayName("le forcage motive passe, et laisse une trace d'audit dediee")
    void forcageMotiveTrace() {
        LocalDate date = LocalDate.of(2026, 7, 15);
        Ruche ruche = rucheRecoltee(date);
        when(ruche.getModele()).thenReturn("Dadant");
        // Le traitement se construit AVANT le `when` : l'imbriquer produirait un
        // stubbing inacheve, Mockito n'admettant pas qu'on stube pendant qu'on stube.
        Traitement bloquant = carence(LocalDate.of(2026, 7, 25), 5L);
        when(traitements.sousCarenceAu(date)).thenReturn(List.of(bloquant));
        when(recoltes.countByRuche_IdAndDateRecolte(eq(5L), eq(date))).thenReturn(0L);
        when(recoltes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecolteReponse r = service().creer(new RecolteCorps(5L, date, BigDecimal.ONE, null, null, null, null,
                true, "Hausse posee apres la fin du traitement, miel non expose."));

        assertThat(r.lot()).isEqualTo("ZUMM-5-20260715-01");
        // La creation est deja auditee par l'aspect ; ce qu'il ne sait pas dire,
        // c'est qu'une regle a ete ecartee. C'est cette entree-la qu'un controle
        // vient chercher.
        var capture = org.mockito.ArgumentCaptor.forClass(AuditEntree.class);
        org.mockito.Mockito.verify(audits).save(capture.capture());
        assertThat(capture.getValue().getAction()).isEqualTo(AuditEntree.FORCAGE);
        assertThat(capture.getValue().getResume()).contains("Hausse posee apres la fin");
    }

    @Test
    @DisplayName("une carence sur une AUTRE ruche ne bloque rien")
    void carenceDUneAutreRuche() {
        LocalDate date = LocalDate.of(2026, 7, 15);
        Ruche ruche = rucheRecoltee(date);
        when(ruche.getModele()).thenReturn("Dadant");
        Traitement ailleurs = carence(LocalDate.of(2026, 7, 25), 99L);
        when(traitements.sousCarenceAu(date)).thenReturn(List.of(ailleurs));
        when(recoltes.countByRuche_IdAndDateRecolte(eq(5L), eq(date))).thenReturn(0L);
        when(recoltes.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service().creer(corps(5L, date, BigDecimal.ONE, null)).lot())
                .isEqualTo("ZUMM-5-20260715-01");
    }
}
