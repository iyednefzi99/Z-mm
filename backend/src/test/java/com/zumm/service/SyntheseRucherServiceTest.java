package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.Alerte;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.Tache;
import com.zumm.domain.Traitement;
import com.zumm.repository.AlerteRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.IndiceColonie;
import com.zumm.web.dto.SyntheseRucher;
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
 * Synthèse par rucher (SPRINT-23, lot 3 du plan de couverture).
 *
 * <p>81,8 % d'instructions mais <strong>35,7 % de branches</strong> : l'écart
 * porte entièrement sur les cas où une donnée manque, et ce sont eux qui
 * décident du sens de l'écran.
 *
 * <p><strong>Trois décisions du SPRINT-23 sont ici, et aucune n'était testée :</strong>
 *
 * <ol>
 *   <li>La <strong>santé moyenne vaut {@code null}</strong> sur un rucher jamais
 *       visité. Compter une colonie non observée comme 0 ferait chuter un rucher
 *       qu'on n'a pas encore vu ; comme 100, il mentirait dans l'autre sens.</li>
 *   <li>Le risque d'essaimage remonté est le <strong>maximum</strong>, pas la
 *       moyenne : une colonie prête à essaimer ne se dilue pas dans trente
 *       colonies calmes.</li>
 *   <li>L'ordre est celui du <strong>travail</strong> — carences, puis alertes,
 *       puis taille. Un tri alphabétique obligerait à chercher.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SyntheseRucherServiceTest {

    @Mock private SiteRepository sites;
    @Mock private RucheRepository ruches;
    @Mock private RecolteRepository recoltes;
    @Mock private AlerteRepository alertes;
    @Mock private TacheRepository taches;
    @Mock private TraitementRepository traitements;
    @Mock private IndiceColonieService indices;

    private SyntheseRucherService service;

    @BeforeEach
    void monter() {
        service = new SyntheseRucherService(sites, ruches, recoltes, alertes, taches,
                traitements, indices);
        lenient().when(indices.parc()).thenReturn(List.of());
        lenient().when(alertes.findByOuverteTrueOrderByOuverteLeDesc()).thenReturn(List.of());
        lenient().when(taches.findAll()).thenReturn(List.of());
        lenient().when(traitements.sousCarenceAu(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        lenient().when(recoltes.findByOrderByDateRecolteDescIdDesc()).thenReturn(List.of());
        lenient().when(ruches.findAll()).thenReturn(List.of());
    }

    private static Site site(long id, String nom, String ville, String priorite) {
        Site s = mock(Site.class);
        lenient().when(s.getId()).thenReturn(id);
        lenient().when(s.getNom()).thenReturn(nom);
        lenient().when(s.getVille()).thenReturn(ville);
        lenient().when(s.getPriorite()).thenReturn(priorite);
        return s;
    }

    private static Ruche ruche(long id, Site site, EtatRuche etat) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getSite()).thenReturn(site);
        lenient().when(r.getEtat()).thenReturn(etat);
        return r;
    }

    private static IndiceColonie indice(long rucheId, int sante, int risque, int composantes) {
        return new IndiceColonie(rucheId, "Dadant", sante, risque, composantes,
                LocalDate.now(), List.of());
    }

    // ── Ce qui n'a pas été observé ──────────────────────────────────────────

    @Test
    @DisplayName("un rucher jamais visité rend null, jamais zéro ni cent")
    void rucherJamaisVisite() {
        Site s = site(1L, "Rucher du haut", "Béja", "normale");
        Ruche r10 = ruche(10L, s, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(r10));

        SyntheseRucher synthese = service.tous().get(0);

        // « Inconnu » n'est pas « en mauvaise santé ». C'est la décision du
        // SPRINT-23, et l'écran s'appuie dessus pour ne rien affirmer.
        assertThat(synthese.santeMoyenne()).isNull();
        assertThat(synthese.risqueEssaimageMax()).isNull();
        assertThat(synthese.coloniesEvaluees()).isZero();
        assertThat(synthese.nbRuches()).isEqualTo(1);
    }

    @Test
    @DisplayName("une colonie sans composante ne compte pas dans la moyenne")
    void coloniesNonEvalueesExclues() {
        Site s = site(1L, "Rucher du haut", "Béja", "normale");
        Ruche r10 = ruche(10L, s, EtatRuche.ACTIVE);
        Ruche r11 = ruche(11L, s, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(r10, r11));
        when(indices.parc()).thenReturn(List.of(
                indice(10L, 80, 20, 3),
                indice(11L, 0, 0, 0)));

        SyntheseRucher synthese = service.tous().get(0);

        // La moyenne porte sur UNE colonie sur deux, et le dit : sans
        // `coloniesEvaluees`, « 80 » se lirait comme la moyenne du rucher.
        assertThat(synthese.santeMoyenne()).isEqualTo(80);
        assertThat(synthese.coloniesEvaluees()).isEqualTo(1);
        assertThat(synthese.nbRuches()).isEqualTo(2);
    }

    @Test
    @DisplayName("le risque remonté est le MAXIMUM, jamais la moyenne")
    void risqueMaximum() {
        Site s = site(1L, "Rucher du haut", null, null);
        Ruche r10 = ruche(10L, s, EtatRuche.ACTIVE);
        Ruche r11 = ruche(11L, s, EtatRuche.ACTIVE);
        Ruche r12 = ruche(12L, s, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(r10, r11, r12));
        when(indices.parc()).thenReturn(List.of(
                indice(10L, 90, 0, 2), indice(11L, 90, 0, 2), indice(12L, 90, 90, 2)));

        SyntheseRucher synthese = service.tous().get(0);

        // La moyenne dirait 30, et l'apiculteur ne se déplacerait pas. Une
        // colonie prête à essaimer ne se dilue pas dans les calmes.
        assertThat(synthese.risqueEssaimageMax()).isEqualTo(90);
        assertThat(synthese.santeMoyenne()).isEqualTo(90);
    }

    @Test
    @DisplayName("un rucher sans aucune ruche sort avec des compteurs à zéro, pas en erreur")
    void rucherVide() {
        Site neuf = site(1L, "Rucher neuf", null, null);
        when(sites.findAll()).thenReturn(List.of(neuf));

        SyntheseRucher synthese = service.tous().get(0);

        assertThat(synthese.nbRuches()).isZero();
        assertThat(synthese.nbActives()).isZero();
        assertThat(synthese.santeMoyenne()).isNull();
        assertThat(synthese.productionKg()).isEqualByComparingTo("0");
    }

    // ── Comptages ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("les ruches clôturées comptent dans le total, pas dans les actives")
    void ruchesCloturees() {
        Site s = site(1L, "Rucher du haut", null, null);
        Ruche active = ruche(10L, s, EtatRuche.ACTIVE);
        Ruche cloturee = ruche(11L, s, EtatRuche.CLOTUREE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(active, cloturee));

        SyntheseRucher synthese = service.tous().get(0);

        // Une ruche clôturée reste au registre : la faire disparaître du total
        // ferait varier l'historique du rucher rétroactivement.
        assertThat(synthese.nbRuches()).isEqualTo(2);
        assertThat(synthese.nbActives()).isEqualTo(1);
    }

    @Test
    @DisplayName("une ruche sans rucher n'est comptée nulle part")
    void rucheSansRucher() {
        Site s = site(1L, "Rucher du haut", null, null);
        Ruche rattachee = ruche(10L, s, EtatRuche.ACTIVE);
        Ruche orpheline = ruche(11L, null, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(rattachee, orpheline));

        assertThat(service.tous().get(0).nbRuches()).isEqualTo(1);
    }

    @Test
    @DisplayName("alertes ouvertes et tâches non faites sont sommées par rucher")
    void alertesEtTaches() {
        Site s = site(1L, "Rucher du haut", null, null);
        Ruche r10 = ruche(10L, s, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(r10));

        Alerte a1 = mock(Alerte.class);
        Alerte a2 = mock(Alerte.class);
        Alerte orpheline = mock(Alerte.class);
        when(a1.getRuche()).thenReturn(r10);
        when(a2.getRuche()).thenReturn(r10);
        when(orpheline.getRuche()).thenReturn(null);
        when(alertes.findByOuverteTrueOrderByOuverteLeDesc())
                .thenReturn(List.of(a1, a2, orpheline));

        Tache faite = mock(Tache.class);
        Tache aFaire = mock(Tache.class);
        Tache sansRuche = mock(Tache.class);
        when(faite.isFaite()).thenReturn(true);
        when(aFaire.isFaite()).thenReturn(false);
        when(aFaire.getRuche()).thenReturn(r10);
        when(sansRuche.isFaite()).thenReturn(false);
        when(sansRuche.getRuche()).thenReturn(null);
        when(taches.findAll()).thenReturn(List.of(faite, aFaire, sansRuche));

        SyntheseRucher synthese = service.tous().get(0);

        assertThat(synthese.alertesOuvertes()).isEqualTo(2);
        // Une tâche faite n'est pas une tâche : la compter transformerait le
        // compteur en historique, et il ne baisserait jamais.
        assertThat(synthese.tachesOuvertes()).isEqualTo(1);
    }

    @Test
    @DisplayName("un traitement qui n'est plus sous carence ne compte pas")
    void carenceEffective() {
        Site s = site(1L, "Rucher du haut", null, null);
        Ruche r10 = ruche(10L, s, EtatRuche.ACTIVE);
        Ruche r11 = ruche(11L, s, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(r10, r11));

        Traitement encore = mock(Traitement.class);
        Traitement fini = mock(Traitement.class);
        when(encore.sousCarence(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(encore.getRuche()).thenReturn(r10);
        when(fini.sousCarence(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(traitements.sousCarenceAu(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(encore, fini));

        // Le dépôt présélectionne, le service tranche : c'est cette seconde
        // vérification qui rend la carence opposable au jour près.
        assertThat(service.tous().get(0).ruchesSousCarence()).isEqualTo(1);
    }

    @Test
    @DisplayName("la production somme la saison en cours, pas l'historique")
    void productionDeLaSaison() {
        Site s = site(1L, "Rucher du haut", null, null);
        Ruche r10 = ruche(10L, s, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(s));
        when(ruches.findAll()).thenReturn(List.of(r10));

        Recolte recente = mock(Recolte.class);
        Recolte ancienne = mock(Recolte.class);
        when(recente.getDateRecolte()).thenReturn(LocalDate.now().minusDays(30));
        when(recente.getQuantiteKg()).thenReturn(new BigDecimal("18.500"));
        when(recente.getRuche()).thenReturn(r10);
        when(ancienne.getDateRecolte()).thenReturn(LocalDate.now().minusDays(400));
        when(recoltes.findByOrderByDateRecolteDescIdDesc())
                .thenReturn(List.of(recente, ancienne));

        // Une récolte d'il y a plus d'un an appartient à la saison précédente :
        // l'additionner ferait croire à un rucher deux fois plus productif.
        assertThat(service.tous().get(0).productionKg()).isEqualByComparingTo("18.500");
    }

    // ── Ordre ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("l'ordre suit le travail : carences, puis alertes, puis taille")
    void ordreDuTravail() {
        Site calme = site(1L, "Calme", null, null);
        Site alerte = site(2L, "Avec alerte", null, null);
        Site carence = site(3L, "Sous carence", null, null);
        Ruche rCalme = ruche(10L, calme, EtatRuche.ACTIVE);
        Ruche rCalme2 = ruche(13L, calme, EtatRuche.ACTIVE);
        Ruche rAlerte = ruche(11L, alerte, EtatRuche.ACTIVE);
        Ruche rCarence = ruche(12L, carence, EtatRuche.ACTIVE);

        when(sites.findAll()).thenReturn(List.of(calme, alerte, carence));
        when(ruches.findAll()).thenReturn(List.of(rCalme, rCalme2, rAlerte, rCarence));

        Alerte a = mock(Alerte.class);
        when(a.getRuche()).thenReturn(rAlerte);
        when(alertes.findByOuverteTrueOrderByOuverteLeDesc()).thenReturn(List.of(a));

        Traitement t = mock(Traitement.class);
        when(t.sousCarence(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(t.getRuche()).thenReturn(rCarence);
        when(traitements.sousCarenceAu(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(t));

        List<SyntheseRucher> ordre = service.tous();

        // Un tri alphabétique mettrait « Avec alerte » en tête et « Sous
        // carence » en dernier : exactement l'inverse de ce qu'il faut faire.
        assertThat(ordre).extracting(SyntheseRucher::siteNom)
                .containsExactly("Sous carence", "Avec alerte", "Calme");
    }

    @Test
    @DisplayName("à égalité de carences et d'alertes, le plus grand rucher passe devant")
    void departageParTaille() {
        Site petit = site(1L, "Petit", null, null);
        Site grand = site(2L, "Grand", null, null);
        Ruche unePetite = ruche(10L, petit, EtatRuche.ACTIVE);
        Ruche uneGrande = ruche(11L, grand, EtatRuche.ACTIVE);
        Ruche deuxGrande = ruche(12L, grand, EtatRuche.ACTIVE);
        when(sites.findAll()).thenReturn(List.of(petit, grand));
        when(ruches.findAll()).thenReturn(List.of(unePetite, uneGrande, deuxGrande));

        assertThat(service.tous()).extracting(SyntheseRucher::siteNom)
                .containsExactly("Grand", "Petit");
    }

    // ── Un seul rucher ──────────────────────────────────────────────────────

    @Test
    @DisplayName("pourSite rend la synthèse du rucher demandé")
    void pourSite() {
        Site s = site(1L, "Rucher du haut", "Béja", "haute");
        Ruche r10 = ruche(10L, s, EtatRuche.ACTIVE);
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(ruches.findAll()).thenReturn(List.of(r10));

        SyntheseRucher synthese = service.pourSite(1L);

        assertThat(synthese.siteNom()).isEqualTo("Rucher du haut");
        assertThat(synthese.ville()).isEqualTo("Béja");
        assertThat(synthese.priorite()).isEqualTo("haute");
    }

    @Test
    @DisplayName("un rucher inconnu est refusé, pas rendu vide")
    void siteIntrouvable() {
        when(sites.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pourSite(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }
}
