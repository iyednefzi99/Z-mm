package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.CouvertSolRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.dto.CorrelationFlore;
import com.zumm.web.dto.IndiceColonie;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests du croisement sante × flore (SPRINT-33).
 *
 * <p>Le calcul de Pearson est deja verrouille par {@code CorrelationMeteoServiceTest}
 * — les deux passent par {@link Coefficient}. Ce qui merite d'etre verrouille ici,
 * c'est ce que le service <strong>refuse de compter</strong> : la ligne 🟡 du §2 de
 * l'ecart concurrentiel disait qu'une correlation sur une dizaine de ruchers serait
 * du bruit presente comme un resultat, et ces tests sont la garantie que ce n'est
 * pas ce qui arrive.
 */
@ExtendWith(MockitoExtension.class)
class CorrelationFloreServiceTest {

    @Mock
    private RucheRepository ruches;
    @Mock
    private CouvertSolRepository couverts;
    @Mock
    private IndiceColonieService indices;
    @Mock
    private ConfigurationMetier configuration;

    private static final int MILLESIME = 2026;

    private CorrelationFloreService service() {
        return new CorrelationFloreService(ruches, couverts, indices, configuration);
    }

    /** Une ruche du rucher donne. */
    private Ruche ruche(long id, long siteId) {
        Site site = mock(Site.class);
        lenient().when(site.getId()).thenReturn(siteId);
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getSite()).thenReturn(site);
        return r;
    }

    private IndiceColonie indice(long rucheId, int sante) {
        return new IndiceColonie(rucheId, "Dadant", sante, 0, 3, null, List.of());
    }

    /**
     * Monte {@code nombre} ruchers d'une ruche chacun : la part de cultures et la
     * sante varient ensemble, ce qui doit donner un lien marque positif.
     */
    private void ruchersCorreles(int nombre) {
        List<Ruche> parc = new ArrayList<>();
        List<IndiceColonie> lesIndices = new ArrayList<>();
        List<CouvertSolRepository.PartSite> parts = new ArrayList<>();
        for (int i = 1; i <= nombre; i++) {
            parc.add(ruche(i, i));
            lesIndices.add(indice(i, 50 + i * 2));
            // Rayon de 1 km : le cercle vaut 314,16 ha, et la surface croit avec
            // l'indice — la part suit donc la sante.
            parts.add(new CouvertSolRepository.PartSite((long) i, BigDecimal.ONE, "culture",
                    BigDecimal.valueOf(i * 10L)));
        }
        when(ruches.findAll()).thenReturn(parc);
        when(indices.parc()).thenReturn(lesIndices);
        when(couverts.millesimes()).thenReturn(List.of(MILLESIME));
        when(couverts.partsParSite(MILLESIME, 3)).thenReturn(parts);
        SeuilsMetier seuils = mock(SeuilsMetier.class);
        lenient().when(seuils.rayonButinageKm()).thenReturn(3);
        when(configuration.seuils()).thenReturn(seuils);
    }

    @Test
    @DisplayName("sous douze ruchers, aucun verdict n'est rendu")
    void echantillonInsuffisant() {
        // Huit ruchers PARFAITEMENT correles : le coefficient vaut 1, et c'est
        // precisement le cas qu'un tableau de bord ferait retenir. Le service rend
        // le chiffre et refuse de le lire — l'objection du SPRINT-32 portait sur
        // la lecture, pas sur le calcul.
        ruchersCorreles(8);

        List<CorrelationFlore> resultat = service().calculer(null);

        assertThat(resultat).hasSize(1);
        assertThat(resultat.get(0).echantillon()).isEqualTo(8);
        assertThat(resultat.get(0).interpretation()).isEqualTo("echantillon_insuffisant");
    }

    @Test
    @DisplayName("a douze ruchers, le lien est enfin lu")
    void echantillonSuffisant() {
        ruchersCorreles(12);

        List<CorrelationFlore> resultat = service().calculer(null);

        assertThat(resultat.get(0).classe()).isEqualTo("culture");
        assertThat(resultat.get(0).echantillon()).isEqualTo(12);
        assertThat(resultat.get(0).interpretation()).isEqualTo("lien_marque_positif");
        assertThat(resultat.get(0).coefficient()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("une colonie sans observation ne compte pas pour une sante nulle")
    void colonieNonEvalueeEcartee() {
        Ruche evaluee = ruche(1, 1);
        Ruche jamaisOuverte = ruche(2, 1);
        when(ruches.findAll()).thenReturn(List.of(evaluee, jamaisOuverte));
        // `composantes = 0` : la ruche est INCONNUE, pas malade.
        when(indices.parc()).thenReturn(List.of(indice(1, 80),
                new IndiceColonie(2L, "Dadant", 100, 0, 0, null, List.of())));
        when(couverts.millesimes()).thenReturn(List.of(MILLESIME));
        when(couverts.partsParSite(MILLESIME, 3)).thenReturn(List.of(
                new CouvertSolRepository.PartSite(1L, BigDecimal.ONE, "culture",
                        BigDecimal.TEN)));
        SeuilsMetier seuils = mock(SeuilsMetier.class);
        lenient().when(seuils.rayonButinageKm()).thenReturn(3);
        when(configuration.seuils()).thenReturn(seuils);

        List<CorrelationFlore> resultat = service().calculer(null);

        // Un seul rucher apparie : la moyenne porte sur la seule colonie evaluee.
        // La faire entrer a 0 aurait fait plonger la sante des ruchers les moins
        // visites, c'est-a-dire ceux dont on sait le moins.
        assertThat(resultat.get(0).echantillon()).isEqualTo(1);
    }

    @Test
    @DisplayName("sans couche versee, rien n'est rendu plutot que dix liens nuls")
    void aucuneCouche() {
        when(couverts.millesimes()).thenReturn(List.of());

        assertThat(service().calculer(null)).isEmpty();
    }

    @Test
    @DisplayName("un rucher hors de toute couche est ecarte, jamais compte pour 0 %")
    void rucherHorsCouche() {
        // Les deux mocks sont construits AVANT d'ouvrir le stubbing : `ruche(...)`
        // stube, et stuber pendant qu'un `when(...)` est ouvert casse Mockito.
        Ruche premier = ruche(1, 1);
        Ruche second = ruche(2, 2);
        when(ruches.findAll()).thenReturn(List.of(premier, second));
        when(indices.parc()).thenReturn(List.of(indice(1, 80), indice(2, 40)));
        when(couverts.millesimes()).thenReturn(List.of(MILLESIME));
        // Seul le rucher 1 est decrit par la couche. Le rucher 2 n'est pas « sans
        // cultures » : son environnement est INCONNU, et l'entrer a 0 % ferait
        // entrer une ignorance dans le calcul comme une mesure.
        when(couverts.partsParSite(MILLESIME, 3)).thenReturn(List.of(
                new CouvertSolRepository.PartSite(1L, BigDecimal.ONE, "culture",
                        BigDecimal.TEN)));
        SeuilsMetier seuils = mock(SeuilsMetier.class);
        lenient().when(seuils.rayonButinageKm()).thenReturn(3);
        when(configuration.seuils()).thenReturn(seuils);

        assertThat(service().calculer(null).get(0).echantillon()).isEqualTo(1);
    }
}
