package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.Ferme;
import com.zumm.domain.Fermier;
import com.zumm.domain.LotConditionnement;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.FermierRepository;
import com.zumm.repository.LotConditionnementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.ResultatRecherche;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Recherche transverse (SPRINT-21, lot 3 du plan de couverture).
 *
 * <p>52,9 % d'instructions et <strong>41,7 % de branches</strong> pour la
 * palette qui interroge sept familles d'objets à la fois. Ce que ces tests
 * verrouillent n'est pas le résultat mais ses <strong>bornes</strong>, et
 * chacune est une décision de sécurité écrite dans la javadoc du service :
 *
 * <ul>
 *   <li><strong>aucune position, aucune adresse</strong> — chercher par rue
 *       rendrait interrogeable ce que {@code PolitiquePositions} masque à
 *       l'affichage ;</li>
 *   <li><strong>aucun courriel</strong> — chercher par adresse transformerait la
 *       palette en annuaire exportable ;</li>
 *   <li><strong>deux caractères au minimum, et un plafond</strong> — une
 *       recherche sur « a » n'est pas une recherche, c'est un export.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RechercheServiceTest {

    @Mock private SiteRepository sites;
    @Mock private RucheRepository ruches;
    @Mock private FermeRepository fermes;
    @Mock private FermierRepository fermiers;
    @Mock private AgentRepository agents;
    @Mock private RecolteRepository recoltes;
    @Mock private LotConditionnementRepository lots;

    private RechercheService service;

    @BeforeEach
    void monter() {
        service = new RechercheService(sites, ruches, fermes, fermiers, agents, recoltes, lots);
        lenient().when(ruches.rechercher(anyString(), any())).thenReturn(List.of());
        lenient().when(sites.rechercher(anyString(), any())).thenReturn(List.of());
        lenient().when(fermes.findByNomContainingIgnoreCaseOrderByNomAsc(anyString(), any()))
                .thenReturn(List.of());
        lenient().when(fermiers.findByNomContainingIgnoreCaseOrderByNomAsc(anyString(), any()))
                .thenReturn(List.of());
        lenient().when(agents.findByNomContainingIgnoreCaseOrderByNomAsc(anyString(), any()))
                .thenReturn(List.of());
        lenient().when(recoltes.rechercher(anyString(), any())).thenReturn(List.of());
        lenient().when(lots.findByReferenceContainingIgnoreCaseOrderByReferenceAsc(
                anyString(), any())).thenReturn(List.of());
    }

    private static Ruche ruche(long id, String modele, Site site) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getModele()).thenReturn(modele);
        lenient().when(r.getSite()).thenReturn(site);
        return r;
    }

    private static Site site(long id, String nom, String ville) {
        Site s = mock(Site.class);
        lenient().when(s.getId()).thenReturn(id);
        lenient().when(s.getNom()).thenReturn(nom);
        lenient().when(s.getVille()).thenReturn(ville);
        return s;
    }

    // ── Les bornes ──────────────────────────────────────────────────────────

    @ParameterizedTest(name = "« {0} »")
    @ValueSource(strings = {"", " ", "a", "  b  "})
    @DisplayName("un motif trop court est refusé : ce serait un export, pas une recherche")
    void motifTropCourt(String motif) {
        assertThatThrownBy(() -> service.rechercher(motif, null))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("2");
    }

    @Test
    @DisplayName("un motif absent est refusé comme un motif vide")
    void motifNul() {
        assertThatThrownBy(() -> service.rechercher(null, null))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("le motif est débarrassé de ses espaces avant d'atteindre les dépôts")
    void motifNettoye() {
        service.rechercher("  tilleul  ", null);

        ArgumentCaptor<String> terme = ArgumentCaptor.forClass(String.class);
        verify(ruches).rechercher(terme.capture(), any());
        assertThat(terme.getValue()).isEqualTo("tilleul");
    }

    @Test
    @DisplayName("chaque famille est interrogée avec le même plafond de cinq")
    void plafondParFamille() {
        service.rechercher("tilleul", null);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(ruches).rechercher(eq("tilleul"), page.capture());
        // Cinq par famille : sans ce plafond, une famille prolifique évincerait
        // les six autres et la palette ne servirait plus qu'à une chose.
        assertThat(page.getValue().getPageSize()).isEqualTo(5);
        assertThat(page.getValue().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("les sept familles sont interrogées, aucune n'est oubliée")
    void septFamilles() {
        service.rechercher("tilleul", null);

        verify(ruches).rechercher(eq("tilleul"), any());
        verify(sites).rechercher(eq("tilleul"), any());
        verify(fermes).findByNomContainingIgnoreCaseOrderByNomAsc(eq("tilleul"), any());
        verify(fermiers).findByNomContainingIgnoreCaseOrderByNomAsc(eq("tilleul"), any());
        verify(agents).findByNomContainingIgnoreCaseOrderByNomAsc(eq("tilleul"), any());
        verify(recoltes).rechercher(eq("tilleul"), any());
        verify(lots).findByReferenceContainingIgnoreCaseOrderByReferenceAsc(eq("tilleul"), any());
    }

    @Test
    @DisplayName("le plafond total borne le résultat, même si les familles en rendent plus")
    void plafondTotal() {
        List<Ruche> cinq = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> ruche(i, "Dadant " + i, null)).toList();
        List<Site> cinqSites = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> site(i, "Rucher " + i, null)).toList();
        when(ruches.rechercher(anyString(), any())).thenReturn(cinq);
        when(sites.rechercher(anyString(), any())).thenReturn(cinqSites);

        assertThat(service.rechercher("rucher", 3)).hasSize(3);
        assertThat(service.rechercher("rucher", null)).hasSize(10);
    }

    @Test
    @DisplayName("une limite absurde est ramenée dans les bornes, pas refusée")
    void limiteHorsBornes() {
        List<Ruche> cinq = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> ruche(i, "Dadant " + i, null)).toList();
        when(ruches.rechercher(anyString(), any())).thenReturn(cinq);

        // Zéro ou négatif remonte à 1 ; mille redescend à 25. Refuser en 400
        // coûterait plus à l'utilisateur que de borner.
        assertThat(service.rechercher("dadant", 0)).hasSize(1);
        assertThat(service.rechercher("dadant", -5)).hasSize(1);
        assertThat(service.rechercher("dadant", 1000)).hasSize(5);
    }

    // ── Ce qui ne sort pas ──────────────────────────────────────────────────

    @Test
    @DisplayName("un résultat de rucher porte le nom et la commune, jamais la position")
    void aucunePositionDansLesResultats() {
        Site s = site(1L, "Rucher des tilleuls", "Béja");
        when(sites.rechercher(anyString(), any())).thenReturn(List.of(s));

        ResultatRecherche resultat = service.rechercher("tilleul", null).get(0);

        // La commune situe à la ville, la rue situerait au portail. C'est
        // exactement la ligne que `PolitiquePositions` tient à l'affichage, et
        // une recherche par adresse la contournerait.
        assertThat(resultat.type()).isEqualTo("site");
        assertThat(resultat.libelle()).isEqualTo("Rucher des tilleuls");
        assertThat(resultat.precision()).isEqualTo("Béja");
        assertThat(resultat.route()).isEqualTo("/sites");
    }

    @Test
    @DisplayName("un agent sort par son nom seul, jamais par son courriel")
    void aucunCourrielDansLesResultats() {
        Agent a = mock(Agent.class);
        when(a.getId()).thenReturn(3L);
        when(a.getNom()).thenReturn("Amal Ben Salah");
        when(agents.findByNomContainingIgnoreCaseOrderByNomAsc(anyString(), any()))
                .thenReturn(List.of(a));

        ResultatRecherche resultat = service.rechercher("amal", null).get(0);

        // Le dépôt est interrogé sur le NOM. Chercher par adresse ferait de la
        // palette un annuaire exportable, un caractère à la fois.
        assertThat(resultat.libelle()).isEqualTo("Amal Ben Salah");
        assertThat(resultat.precision()).isNull();
    }

    // ── Mise en forme ───────────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche est nommée par son identifiant ET son modèle")
    void libelleDUneRuche() {
        Site s = site(1L, "Rucher du haut", null);
        Ruche r = ruche(42L, "Dadant 10 cadres", s);
        when(ruches.rechercher(anyString(), any())).thenReturn(List.of(r));

        ResultatRecherche resultat = service.rechercher("dadant", null).get(0);

        // Quarante ruches « Dadant » ne se distinguent que par leur numéro.
        assertThat(resultat.libelle()).isEqualTo("Ruche 42 — Dadant 10 cadres");
        assertThat(resultat.precision()).isEqualTo("Rucher du haut");
        assertThat(resultat.route()).isEqualTo("/ruches");
    }

    @Test
    @DisplayName("une ruche sans rucher n'invente pas de détail")
    void rucheSansRucher() {
        Ruche r = ruche(42L, "Dadant", null);
        when(ruches.rechercher(anyString(), any())).thenReturn(List.of(r));

        assertThat(service.rechercher("dadant", null).get(0).precision()).isNull();
    }

    @Test
    @DisplayName("une récolte sans lot retombe sur son identifiant")
    void recolteSansLot() {
        Recolte avecLot = mock(Recolte.class);
        Recolte sansLot = mock(Recolte.class);
        when(avecLot.getId()).thenReturn(7L);
        when(avecLot.getLot()).thenReturn("L-2026-1");
        when(avecLot.getTypeMiel()).thenReturn("toutes fleurs");
        when(sansLot.getId()).thenReturn(8L);
        when(sansLot.getLot()).thenReturn(null);
        when(recoltes.rechercher(anyString(), any())).thenReturn(List.of(avecLot, sansLot));

        List<ResultatRecherche> resultats = service.rechercher("2026", null);

        // Une ligne sans libellé serait une ligne qu'on ne peut pas cliquer.
        assertThat(resultats.get(0).libelle()).isEqualTo("L-2026-1");
        assertThat(resultats.get(0).precision()).isEqualTo("toutes fleurs");
        assertThat(resultats.get(1).libelle()).isEqualTo("Recolte 8");
    }

    @Test
    @DisplayName("fermes, fermiers et lots portent leur chemin d'ouverture")
    void cheminsDOuverture() {
        Ferme ferme = mock(Ferme.class);
        Fermier fermier = mock(Fermier.class);
        LotConditionnement lot = mock(LotConditionnement.class);
        when(ferme.getId()).thenReturn(1L);
        when(ferme.getNom()).thenReturn("Ferme des tilleuls");
        when(fermier.getId()).thenReturn(2L);
        when(fermier.getNom()).thenReturn("Ben Salah");
        when(lot.getId()).thenReturn(3L);
        when(lot.getReference()).thenReturn("L-2026-1");
        when(lot.getTypeMiel()).thenReturn("acacia");
        when(fermes.findByNomContainingIgnoreCaseOrderByNomAsc(anyString(), any()))
                .thenReturn(List.of(ferme));
        when(fermiers.findByNomContainingIgnoreCaseOrderByNomAsc(anyString(), any()))
                .thenReturn(List.of(fermier));
        when(lots.findByReferenceContainingIgnoreCaseOrderByReferenceAsc(anyString(), any()))
                .thenReturn(List.of(lot));

        List<ResultatRecherche> resultats = service.rechercher("tilleul", null);

        // Un résultat qu'on ne sait pas ouvrir n'est pas un résultat.
        assertThat(resultats).extracting(ResultatRecherche::route)
                .containsExactly("/fermes", "/fermiers", "/lots");
    }

    @Test
    @DisplayName("l'ordre suit celui du rucher : ce qu'on a sous les yeux d'abord")
    void ordreDesFamilles() {
        Ruche r = ruche(42L, "Dadant", null);
        Site s = site(1L, "Rucher", null);
        Agent a = mock(Agent.class);
        when(a.getId()).thenReturn(3L);
        when(a.getNom()).thenReturn("Amal");
        when(ruches.rechercher(anyString(), any())).thenReturn(List.of(r));
        when(sites.rechercher(anyString(), any())).thenReturn(List.of(s));
        when(agents.findByNomContainingIgnoreCaseOrderByNomAsc(anyString(), any()))
                .thenReturn(List.of(a));

        // Ruches et sites d'abord — ce qu'on a sous les yeux —, l'organisation
        // ensuite. Un ordre alphabetique par type mettrait « agent » en tete.
        assertThat(service.rechercher("rucher", 25)).extracting(ResultatRecherche::type)
                .containsExactly("ruche", "site", "agent");
    }

    @Test
    @DisplayName("aucun résultat rend une liste vide, pas une erreur")
    void aucunResultat() {
        assertThat(service.rechercher("introuvable", null)).isEmpty();
    }
}
