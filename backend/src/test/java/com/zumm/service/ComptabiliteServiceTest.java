package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.domain.Depense;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.DepenseRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.BilanExploitation;
import com.zumm.web.dto.DepenseCorps;
import com.zumm.web.dto.DepenseReponse;
import com.zumm.web.dto.RentabiliteRuche;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Comptabilité et rentabilité par ruche (SPRINT-27, lot 3 du plan de couverture).
 *
 * <p>Deux affirmations du SPRINT-27 se vérifient ici, et elles sont du même
 * ordre que celles du bilan PDF — sauf qu'ici elles se calculent :
 *
 * <ol>
 *   <li><strong>Seul le miel est valorisé.</strong> Le prix du kilo de
 *       {@code ConfigZumm.ini} est un prix de miel ; l'appliquer à de la cire ou
 *       à un essaim donnerait un chiffre inventé, et rien à l'écran ne dirait
 *       qu'il l'est.</li>
 *   <li><strong>Rien n'est réparti au jugé.</strong> Les dépenses non affectées
 *       à une ruche figurent entières et à part : une clé de répartition
 *       inventée donnerait une rentabilité par ruche plus jolie et moins vraie.</li>
 * </ol>
 *
 * <p>Et une troisième, dans le code depuis le début : <strong>la période est
 * demandée, jamais devinée</strong>. Un bilan sur « les douze derniers mois »
 * recule chaque jour, et deux consultations à une semaine d'écart ne portent
 * alors pas sur la même chose.
 */
@ExtendWith(MockitoExtension.class)
class ComptabiliteServiceTest {

    @Mock private DepenseRepository depenses;
    @Mock private RecolteRepository recoltes;
    @Mock private RucheRepository ruches;
    @Mock private SiteRepository sites;
    @Mock private ConfigurationMetier configuration;

    private ComptabiliteService service;

    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 12, 31);
    /** Prix du kilo de miel, tel que le porte ConfigZumm.ini. */
    private static final BigDecimal PRIX_KG = new BigDecimal("8.00");

    @BeforeEach
    void monter() {
        service = new ComptabiliteService(depenses, recoltes, ruches, sites, configuration);
        SeuilsMetier seuils = mock(SeuilsMetier.class);
        lenient().when(seuils.prixMielKgEur()).thenReturn(PRIX_KG);
        lenient().when(configuration.seuils()).thenReturn(seuils);
        lenient().when(depenses.findByDateDepenseBetweenOrderByDateDepenseDescIdDesc(any(), any()))
                .thenReturn(List.of());
        lenient().when(recoltes.findByOrderByDateRecolteDescIdDesc()).thenReturn(List.of());
        lenient().when(ruches.findAll()).thenReturn(List.of());
    }

    private static Ruche ruche(long id, String modele, Site site) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getModele()).thenReturn(modele);
        lenient().when(r.getSite()).thenReturn(site);
        return r;
    }

    private static Recolte recolte(Ruche ruche, String produit, String kg, LocalDate date) {
        Recolte r = mock(Recolte.class);
        lenient().when(r.getRuche()).thenReturn(ruche);
        lenient().when(r.getDateRecolte()).thenReturn(date);
        lenient().when(r.getQuantiteKg()).thenReturn(new BigDecimal(kg));
        lenient().when(r.estDuMiel()).thenReturn("miel".equals(produit));
        return r;
    }

    private static Depense depense(String categorie, String montant, Ruche ruche) {
        Depense d = mock(Depense.class);
        lenient().when(d.getCategorie()).thenReturn(categorie);
        lenient().when(d.getMontantEur()).thenReturn(new BigDecimal(montant));
        lenient().when(d.getRuche()).thenReturn(ruche);
        return d;
    }

    // ── La période ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("une période inversée ou incomplète est refusée, pas devinée")
    void periodeInvalide() {
        assertThatThrownBy(() -> service.bilan(FIN, DEBUT))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> service.bilan(null, FIN))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> service.bilan(DEBUT, null))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("une période d'un seul jour est valide")
    void periodeDUnJour() {
        assertThat(service.bilan(DEBUT, DEBUT).debut()).isEqualTo(DEBUT);
    }

    @Test
    @DisplayName("une récolte hors période n'entre pas dans le bilan")
    void recolteHorsPeriode() {
        Ruche r = ruche(1L, "Dadant", null);
        Recolte avant = recolte(r, "miel", "20.000", LocalDate.of(2025, 8, 1));
        Recolte dedans = recolte(r, "miel", "10.000", LocalDate.of(2026, 8, 1));
        Recolte apres = recolte(r, "miel", "5.000", LocalDate.of(2027, 1, 5));
        when(ruches.findAll()).thenReturn(List.of(r));
        when(recoltes.findByOrderByDateRecolteDescIdDesc())
                .thenReturn(List.of(avant, dedans, apres));

        BilanExploitation bilan = service.bilan(DEBUT, FIN);

        assertThat(bilan.productionMielKg()).isEqualByComparingTo("10.000");
    }

    @Test
    @DisplayName("les bornes de la période sont incluses")
    void bornesIncluses() {
        Ruche r = ruche(1L, "Dadant", null);
        Recolte auDebut = recolte(r, "miel", "3.000", DEBUT);
        Recolte aLaFin = recolte(r, "miel", "4.000", FIN);
        when(ruches.findAll()).thenReturn(List.of(r));
        when(recoltes.findByOrderByDateRecolteDescIdDesc())
                .thenReturn(List.of(auDebut, aLaFin));

        assertThat(service.bilan(DEBUT, FIN).productionMielKg()).isEqualByComparingTo("7.000");
    }

    // ── Valorisation ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("valorisation")
    class Valorisation {

        @Test
        @DisplayName("seul le MIEL est valorisé au prix du kilo")
        void seulLeMielEstValorise() {
            Ruche r = ruche(1L, "Dadant", null);
            Recolte miel = recolte(r, "miel", "10.000", LocalDate.of(2026, 8, 1));
            Recolte cire = recolte(r, "cire", "3.000", LocalDate.of(2026, 8, 2));
            Recolte essaim = recolte(r, "essaim", "4", LocalDate.of(2026, 5, 1));
            when(ruches.findAll()).thenReturn(List.of(r));
            when(recoltes.findByOrderByDateRecolteDescIdDesc())
                    .thenReturn(List.of(miel, cire, essaim));

            BilanExploitation bilan = service.bilan(DEBUT, FIN);

            // Quatre essaims et trois kilos de cire ne se valorisent pas à un
            // prix de miel : ils ne comptent ni en kilos ni en euros.
            assertThat(bilan.productionMielKg()).isEqualByComparingTo("10.000");
            assertThat(bilan.recettesEur()).isEqualByComparingTo("80.00");
        }

        @Test
        @DisplayName("aucune récolte de miel : recettes nulles, pas d'erreur")
        void aucunMiel() {
            BilanExploitation bilan = service.bilan(DEBUT, FIN);

            assertThat(bilan.productionMielKg()).isEqualByComparingTo("0");
            assertThat(bilan.recettesEur()).isEqualByComparingTo("0.00");
            assertThat(bilan.parRuche()).isEmpty();
        }
    }

    // ── Dépenses ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("dépenses")
    class Depenses {

        @Test
        @DisplayName("les non affectées sont comptées entières et à part")
        void nonAffecteesAPart() {
            Ruche r = ruche(1L, "Dadant", null);
            Depense surLaRuche = depense("consommable", "60.00", r);
            Depense assurance = depense("assurance", "310.00", null);
            when(ruches.findAll()).thenReturn(List.of(r));
            when(depenses.findByDateDepenseBetweenOrderByDateDepenseDescIdDesc(any(), any()))
                    .thenReturn(List.of(surLaRuche, assurance));

            BilanExploitation bilan = service.bilan(DEBUT, FIN);

            assertThat(bilan.depensesEur()).isEqualByComparingTo("370.00");
            assertThat(bilan.depensesNonAffectees()).isEqualByComparingTo("310.00");
            // Les 310 € d'assurance ne descendent PAS sur la ruche : une clé de
            // répartition inventée donnerait un résultat plus joli et moins vrai.
            assertThat(bilan.parRuche().get(0).depensesEur()).isEqualByComparingTo("60.00");
        }

        @Test
        @DisplayName("la ventilation par poste va du plus gros au plus petit")
        void ventilationTriee() {
            Depense materielUn = depense("materiel", "100.00", null);
            Depense assurance = depense("assurance", "310.00", null);
            Depense consommable = depense("consommable", "200.00", null);
            Depense materielDeux = depense("materiel", "50.00", null);
            when(depenses.findByDateDepenseBetweenOrderByDateDepenseDescIdDesc(any(), any()))
                    .thenReturn(List.of(materielUn, assurance, consommable, materielDeux));

            BilanExploitation bilan = service.bilan(DEBUT, FIN);

            // Du plus gros poste au plus petit : c'est là qu'on agit. Et les
            // deux lignes « materiel » se somment avant d'être classées.
            assertThat(bilan.parCategorie().keySet())
                    .containsExactly("assurance", "consommable", "materiel");
            assertThat(bilan.parCategorie().get("materiel")).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("aucune dépense : ventilation vide, totaux à zéro")
        void aucuneDepense() {
            BilanExploitation bilan = service.bilan(DEBUT, FIN);

            assertThat(bilan.parCategorie()).isEmpty();
            assertThat(bilan.depensesEur()).isEqualByComparingTo("0.00");
            assertThat(bilan.depensesNonAffectees()).isEqualByComparingTo("0.00");
        }
    }

    // ── Rentabilité par ruche ───────────────────────────────────────────────

    @Test
    @DisplayName("les ruches sont classées du plus rentable au moins rentable")
    void ordreParRentabilite() {
        Site site = mock(Site.class);
        lenient().when(site.getNom()).thenReturn("Rucher du haut");
        Ruche bonne = ruche(1L, "Bonne", site);
        Ruche mauvaise = ruche(2L, "Mauvaise", null);
        Recolte laRecolte = recolte(bonne, "miel", "20.000", LocalDate.of(2026, 8, 1));
        Depense leTraitement = depense("traitement", "40.00", mauvaise);
        when(ruches.findAll()).thenReturn(List.of(mauvaise, bonne));
        when(recoltes.findByOrderByDateRecolteDescIdDesc()).thenReturn(List.of(laRecolte));
        when(depenses.findByDateDepenseBetweenOrderByDateDepenseDescIdDesc(any(), any()))
                .thenReturn(List.of(leTraitement));

        List<RentabiliteRuche> parRuche = service.bilan(DEBUT, FIN).parRuche();

        // La question posée est « lesquelles portent l'exploitation », pas
        // « lesquelles ont le plus petit identifiant ».
        assertThat(parRuche).extracting(RentabiliteRuche::rucheModele)
                .containsExactly("Bonne", "Mauvaise");
        assertThat(parRuche.get(0).resultatEur()).isEqualByComparingTo("160.00");
        assertThat(parRuche.get(0).siteNom()).isEqualTo("Rucher du haut");
        // Une ruche sans production ET avec une dépense est en négatif : le dire
        // est le seul intérêt d'une rentabilité par ruche.
        assertThat(parRuche.get(1).resultatEur()).isEqualByComparingTo("-40.00");
        assertThat(parRuche.get(1).siteNom()).isNull();
    }

    @Test
    @DisplayName("une ruche sans récolte ni dépense sort à zéro, pas absente")
    void rucheSansMouvement() {
        Ruche r = ruche(1L, "Dadant", null);
        when(ruches.findAll()).thenReturn(List.of(r));

        RentabiliteRuche ligne = service.bilan(DEBUT, FIN).parRuche().get(0);

        assertThat(ligne.productionKg()).isEqualByComparingTo("0");
        assertThat(ligne.recettesEur()).isEqualByComparingTo("0.00");
        assertThat(ligne.resultatEur()).isEqualByComparingTo("0.00");
    }

    // ── Saisie ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saisie")
    class Saisie {

        private DepenseCorps corps(Long rucheId, Long siteId) {
            return new DepenseCorps("  Cire gaufrée  ", "consommable",
                    new BigDecimal("120.00"), LocalDate.of(2026, 7, 3), rucheId, siteId, null);
        }

        @Test
        @DisplayName("le libellé est débarrassé de ses espaces de bord")
        void libelleNettoye() {
            when(depenses.save(any())).thenAnswer(i -> i.getArgument(0));

            DepenseReponse reponse = service.creer(corps(null, null));

            // Un libellé qui traîne une espace se trie mal et se cherche mal.
            assertThat(reponse.libelle()).isEqualTo("Cire gaufrée");
        }

        @Test
        @DisplayName("une ruche inconnue dans ce tenant est refusée à la saisie")
        void rucheInconnue() {
            when(ruches.findById(99L)).thenReturn(Optional.empty());

            // Le message dit « dans ce tenant » : la ruche peut exister ailleurs,
            // et c'est justement ce que la RLS empêche de voir.
            assertThatThrownBy(() -> service.creer(corps(99L, null)))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("mettre à jour remplace les champs et réapplique les rattachements")
        void miseAJour() {
            Depense existante = mock(Depense.class);
            when(depenses.findById(5L)).thenReturn(Optional.of(existante));

            service.mettreAJour(5L, corps(null, null));

            verify(existante).setLibelle("Cire gaufrée");
            verify(existante).setMontantEur(new BigDecimal("120.00"));
            verify(existante).setRuche(null);
        }

        @Test
        @DisplayName("une dépense inconnue est refusée en 404, pas ignorée")
        void depenseIntrouvable() {
            when(depenses.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimer(999L))
                    .isInstanceOf(RessourceIntrouvable.class);
        }

        @Test
        @DisplayName("supprimer passe bien l'entité au dépôt")
        void suppression() {
            Depense existante = mock(Depense.class);
            when(depenses.findById(5L)).thenReturn(Optional.of(existante));

            service.supprimer(5L);

            verify(depenses).delete(existante);
        }

        @Test
        @DisplayName("la liste est rendue dans l'ordre du dépôt")
        void liste() {
            Depense uneDepense = depense("materiel", "10.00", null);
            when(depenses.findAllByOrderByDateDepenseDescIdDesc())
                    .thenReturn(List.of(uneDepense));

            assertThat(service.lister()).hasSize(1);
        }
    }
}
