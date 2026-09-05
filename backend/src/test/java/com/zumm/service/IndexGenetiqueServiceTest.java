package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.ComptageVarroa;
import com.zumm.domain.Recolte;
import com.zumm.domain.Reine;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.ComptageVarroaRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.ReleveObservationRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.IndexGenetique;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Index génétique (SPRINT-29, lot D).
 *
 * <p>Le calcul est du raisonnement pur posé sur quatre registres : il se teste
 * sans base, en simulant ce que chaque registre rend. Ce qu'on vérifie ici est
 * ce qui décide si un chiffre est publié ou tu — et c'est précisément ce qu'un
 * test d'intégration met le plus de peine à mettre en scène.
 */
@ExtendWith(MockitoExtension.class)
class IndexGenetiqueServiceTest {

    @Mock private VisiteRepository visites;
    @Mock private RecolteRepository recoltes;
    @Mock private ComptageVarroaRepository comptages;
    @Mock private ReleveObservationRepository releves;

    private IndexGenetiqueService service;

    @BeforeEach
    void vide() {
        service = new IndexGenetiqueService(visites, recoltes, comptages, releves);
        lenient().when(visites.findByRuche_IdAndDateVisiteBetweenOrderByDateVisiteAsc(
                anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(recoltes.findByRuche_IdAndDateRecolteBetweenOrderByDateRecolteAsc(
                anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(comptages.findByRuche_IdAndDateComptageBetweenOrderByDateComptageAsc(
                anyLong(), any(), any())).thenReturn(List.of());
        lenient().when(releves.intensitesPour(anyString(), anyLong(), any(), any()))
                .thenReturn(List.of());
    }

    /** Une reine posée sur une ruche, avec un règne borné. */
    private Reine reine(Long rucheId) {
        Reine r = mock(Reine.class);
        when(r.getId()).thenReturn(7L);
        lenient().when(r.getCode()).thenReturn("R-1");
        lenient().when(r.getDateIntroduction()).thenReturn(LocalDate.of(2026, 6, 1));
        lenient().when(r.getDateFin()).thenReturn(LocalDate.of(2026, 9, 1));
        if (rucheId == null) {
            when(r.getRuche()).thenReturn(null);
        } else {
            Ruche ruche = mock(Ruche.class);
            lenient().when(ruche.getId()).thenReturn(rucheId);
            when(r.getRuche()).thenReturn(ruche);
        }
        return r;
    }

    private Visite visiteTemperament(String temperament) {
        Visite v = mock(Visite.class);
        lenient().when(v.getTemperament()).thenReturn(temperament);
        return v;
    }

    private IndexGenetique.Critere critere(IndexGenetique index, String code) {
        return index.criteres().stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("une reine sans ruche n'a aucun critère, plutôt que cinq zéros")
    void sansRuche() {
        IndexGenetique index = service.pour(reine(null));

        // Tout ce qui se mesure ici se mesure sur une colonie. Rendre des zéros
        // ferait passer une reine en banque à reines pour une mauvaise reine.
        assertThat(index.criteres()).isEmpty();
        assertThat(index.rucheId()).isNull();
    }

    @Test
    @DisplayName("la douceur se tait en dessous de trois observations, puis se calcule")
    void douceur() {
        // Les mocks se construisent AVANT le `when` : les creer dans les
        // arguments de `thenReturn` stube pendant qu'on stube, et Mockito le
        // refuse. Le piege est deja note dans `RecolteServiceTest`.
        List<Visite> deux = List.of(visiteTemperament("doux"), visiteTemperament("doux"));
        when(visites.findByRuche_IdAndDateVisiteBetweenOrderByDateVisiteAsc(
                anyLong(), any(), any())).thenReturn(deux);

        IndexGenetique.Critere avant = critere(service.pour(reine(3L)), "douceur");
        assertThat(avant.suffisant()).isFalse();
        assertThat(avant.valeur()).isNull();
        assertThat(avant.observations()).isEqualTo(2);

        List<Visite> trois = List.of(visiteTemperament("doux"), visiteTemperament("doux"),
                visiteTemperament("normal"));
        when(visites.findByRuche_IdAndDateVisiteBetweenOrderByDateVisiteAsc(
                anyLong(), any(), any())).thenReturn(trois);

        // (3 + 3 + 2) / 3 = 2,7 sur 3.
        IndexGenetique.Critere apres = critere(service.pour(reine(3L)), "douceur");
        assertThat(apres.suffisant()).isTrue();
        assertThat(apres.valeur()).isEqualByComparingTo("2.7");
    }

    @Test
    @DisplayName("un tempérament non renseigné ne compte pas dans les observations")
    void temperamentAbsent() {
        List<Visite> avecTrous = List.of(visiteTemperament("doux"), visiteTemperament(null),
                visiteTemperament(null));
        when(visites.findByRuche_IdAndDateVisiteBetweenOrderByDateVisiteAsc(
                anyLong(), any(), any())).thenReturn(avecTrous);

        // Trois visites, une seule appréciation : compter les trois donnerait un
        // dénominateur qui n'a rien observé.
        assertThat(critere(service.pour(reine(3L)), "douceur").observations()).isEqualTo(1);
    }

    @Test
    @DisplayName("les méthodes de comptage du varroa ne se mélangent pas")
    void varroaUneSeuleMethode() {
        ComptageVarroa parLange = mock(ComptageVarroa.class);
        lenient().when(parLange.getMethode()).thenReturn("lange");
        lenient().when(parLange.parLange()).thenReturn(true);
        lenient().when(parLange.getVarroasComptes()).thenReturn(30);
        lenient().when(parLange.getJoursExposition()).thenReturn(3);
        ComptageVarroa autreLange = mock(ComptageVarroa.class);
        lenient().when(autreLange.getMethode()).thenReturn("lange");
        lenient().when(autreLange.parLange()).thenReturn(true);
        lenient().when(autreLange.getVarroasComptes()).thenReturn(60);
        lenient().when(autreLange.getJoursExposition()).thenReturn(3);
        ComptageVarroa echantillon = mock(ComptageVarroa.class);
        lenient().when(echantillon.getMethode()).thenReturn("sucre_glace");
        List<ComptageVarroa> tous = List.of(parLange, autreLange, echantillon);

        when(comptages.findByRuche_IdAndDateComptageBetweenOrderByDateComptageAsc(
                anyLong(), any(), any())).thenReturn(tous);

        IndexGenetique.Critere varroa =
                critere(service.pour(reine(3L)), "resistance_varroa");

        // Un lange donne des varroas par jour, un échantillon un pourcentage : le
        // SPRINT-20 avait refusé de les confondre, et en faire une moyenne unique
        // ici défairait ce refus. La méthode la plus employée l'emporte, et
        // l'unité le dit.
        assertThat(varroa.unite()).isEqualTo("varroas/jour");
        assertThat(varroa.observations()).isEqualTo(2);
        assertThat(varroa.suffisant()).isTrue();
    }

    @Test
    @DisplayName("un seul comptage ne fait pas une résistance")
    void varroaComptageUnique() {
        ComptageVarroa seul = mock(ComptageVarroa.class);
        lenient().when(seul.getMethode()).thenReturn("sucre_glace");
        lenient().when(seul.parLange()).thenReturn(false);
        when(comptages.findByRuche_IdAndDateComptageBetweenOrderByDateComptageAsc(
                anyLong(), any(), any()))
                .thenReturn(List.of(seul));

        IndexGenetique.Critere varroa =
                critere(service.pour(reine(3L)), "resistance_varroa");

        assertThat(varroa.suffisant()).isFalse();
        assertThat(varroa.unite()).isEqualTo("%");
    }

    @Test
    @DisplayName("seul le miel entre dans la production")
    void productionMielSeul() {
        Recolte miel = mock(Recolte.class);
        lenient().when(miel.estDuMiel()).thenReturn(true);
        lenient().when(miel.getQuantiteKg()).thenReturn(new BigDecimal("12.5"));
        Recolte cire = mock(Recolte.class);
        lenient().when(cire.estDuMiel()).thenReturn(false);

        List<Recolte> recoltees = List.of(miel, cire);
        when(recoltes.findByRuche_IdAndDateRecolteBetweenOrderByDateRecolteAsc(
                anyLong(), any(), any())).thenReturn(recoltees);

        IndexGenetique.Critere production = critere(service.pour(reine(3L)), "production");

        // Additionner des kilogrammes de cire et des essaims comptés à l'unité
        // donnerait un total qui ne veut rien dire.
        assertThat(production.valeur()).isEqualByComparingTo("12.5");
        assertThat(production.observations()).isEqualTo(1);
    }

    @Test
    @DisplayName("le test hygiénique se dit insuffisant tant qu'il n'a pas été relevé")
    void hygiene() {
        IndexGenetique.Critere avant = critere(service.pour(reine(3L)), "hygiene");
        assertThat(avant.suffisant()).isFalse();
        assertThat(avant.observations()).isZero();

        List<Short> notes = List.of((short) 3, (short) 2);
        when(releves.intensitesPour(anyString(), anyLong(), any(), any())).thenReturn(notes);

        IndexGenetique.Critere apres = critere(service.pour(reine(3L)), "hygiene");
        assertThat(apres.suffisant()).isTrue();
        assertThat(apres.valeur()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("l'essaimage compte les visites, pas les jours")
    void essaimage() {
        Visite avecCellules = mock(Visite.class);
        lenient().when(avecCellules.getCellulesRoyales()).thenReturn(4);
        lenient().when(avecCellules.getCellulesRoyalesCause()).thenReturn("essaimage");
        Visite supersedure = mock(Visite.class);
        lenient().when(supersedure.getCellulesRoyales()).thenReturn(2);
        lenient().when(supersedure.getCellulesRoyalesCause()).thenReturn("supersedure");
        Visite calme = mock(Visite.class);
        List<Visite> troisVisites = List.of(avecCellules, supersedure, calme);

        when(visites.findByRuche_IdAndDateVisiteBetweenOrderByDateVisiteAsc(
                anyLong(), any(), any())).thenReturn(troisVisites);

        IndexGenetique.Critere critere = critere(service.pour(reine(3L)), "essaimage");

        // Une supersédure n'est pas un essaimage : la colonie remplace sa reine,
        // elle ne part pas. Les compter ensemble écarterait de bonnes lignées.
        assertThat(critere.valeur()).isEqualByComparingTo("1");
        assertThat(critere.observations()).isEqualTo(3);
        assertThat(critere.unite()).isEqualTo("visites");
    }

    @Test
    @DisplayName("le règne borne la fenêtre, et une reine en service court jusqu'à aujourd'hui")
    void regneOuvert() {
        Reine enService = mock(Reine.class);
        when(enService.getId()).thenReturn(7L);
        when(enService.getDateIntroduction()).thenReturn(LocalDate.of(2026, 6, 1));
        when(enService.getDateFin()).thenReturn(null);
        Ruche ruche = mock(Ruche.class);
        lenient().when(ruche.getId()).thenReturn(3L);
        when(enService.getRuche()).thenReturn(ruche);

        IndexGenetique index = service.pour(enService);

        assertThat(index.debut()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(index.fin()).isEqualTo(LocalDate.now());
    }
}
