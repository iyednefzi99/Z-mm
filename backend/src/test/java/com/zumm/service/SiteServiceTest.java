package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.EmplacementSite;
import com.zumm.domain.Ferme;
import com.zumm.domain.RessourceFlorale;
import com.zumm.domain.Site;
import com.zumm.repository.EmplacementSiteRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.RessourceFloraleRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.securite.PolitiquePositions;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.DemenagementCorps;
import com.zumm.web.dto.RessourceFloraleCorps;
import com.zumm.web.dto.SiteCorps;
import com.zumm.web.dto.SiteReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Ruchers, emplacements et transhumance (US-006, SPRINT-21 ; lot 3 du plan de
 * couverture).
 *
 * <p>90,2 % d'instructions mais <strong>75 % de branches</strong> : l'écart
 * porte sur les refus et sur l'ordre des écritures. Trois décisions s'y jouent,
 * et la dernière est la plus contre-intuitive :
 *
 * <ol>
 *   <li><strong>Déménager n'est pas mettre à jour.</strong> Corriger une position
 *       mal saisie et déplacer un rucher touchent aux mêmes colonnes mais ne
 *       disent pas la même chose ; seule la seconde laisse une trace, sans quoi
 *       l'historique se remplirait de fausses transhumances à chaque faute de
 *       frappe corrigée.</li>
 *   <li><strong>La période précédente se ferme le jour où la suivante s'ouvre.</strong>
 *       Un rucher n'est jamais à deux endroits, ni nulle part entre les deux.</li>
 *   <li><strong>Le {@code flush} n'est pas une précaution.</strong> Hibernate
 *       ordonne ses actions par type — tous les {@code INSERT} d'abord, les
 *       {@code UPDATE} ensuite. Sans lui, le nouvel emplacement serait inséré
 *       avant que l'ancien ne soit clos, et l'index unique partiel
 *       {@code uq_emplacement_courant} ferait échouer <em>tout</em> déménagement
 *       en 500.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SiteServiceTest {

    @Mock private SiteRepository sites;
    @Mock private FermeRepository fermes;
    @Mock private RucheRepository ruches;
    @Mock private RessourceFloraleRepository ressources;
    @Mock private EmplacementSiteRepository emplacements;
    @Mock private PolitiquePositions positions;

    private SiteService service;

    private static final LocalDate INSTALLATION = LocalDate.of(2026, 3, 1);
    private static final BigDecimal LAT = new BigDecimal("36.812345");
    private static final BigDecimal LON = new BigDecimal("10.234567");

    @BeforeEach
    void monter() {
        service = new SiteService(sites, fermes, ruches, ressources, emplacements, positions);
        lenient().when(sites.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(ressources.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(ressources.findBySite_IdOrderByRessourceAsc(any())).thenReturn(List.of());
        // Toute sortie de site passe par le masquage : ici il rend l'objet tel
        // quel, les tests de masquage vivant dans PolitiquePositionsSelonRoleTest.
        lenient().when(positions.masquer(any(SiteReponse.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(positions.masquer(any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(new BigDecimal[] {new BigDecimal("36.81"), new BigDecimal("10.23")});
        lenient().when(positions.positionExacteAutorisee()).thenReturn(false);
    }

    private static Site site(Long id, LocalDate miseEnOeuvre) {
        // La ferme se construit AVANT : `SiteReponse.de` la lit, et un mock cree
        // dans un `thenReturn` laisserait le stub inacheve.
        Ferme f = mock(Ferme.class);
        Site s = mock(Site.class);
        lenient().when(f.getId()).thenReturn(1L);
        lenient().when(f.getNom()).thenReturn("Ferme des tilleuls");
        lenient().when(s.getFerme()).thenReturn(f);
        lenient().when(s.getId()).thenReturn(id);
        lenient().when(s.getNom()).thenReturn("Rucher du haut");
        lenient().when(s.getLatitude()).thenReturn(LAT);
        lenient().when(s.getLongitude()).thenReturn(LON);
        lenient().when(s.getDateMiseEnOeuvre()).thenReturn(miseEnOeuvre);
        return s;
    }

    private static SiteCorps corps(LocalDate demenagement, LocalDate cloture,
            List<RessourceFloraleCorps> florales) {
        return new SiteCorps("Rucher du haut", 1L, LAT, LON, null, null, INSTALLATION,
                demenagement, cloture, "  12 chemin bas  ", "  9000  ", "  Béja  ", "TN",
                "sedentaire", "sud", florales, "normale", "faible");
    }

    // ── Les dates de cycle de vie ───────────────────────────────────────────

    @Test
    @DisplayName("un déménagement antérieur à la mise en œuvre est refusé")
    void demenagementAvantInstallation() {
        assertThatThrownBy(() ->
                service.creer(corps(INSTALLATION.minusDays(1), null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("demenagement precede");
        verify(sites, never()).save(any());
    }

    @Test
    @DisplayName("une clôture antérieure à la mise en œuvre est refusée")
    void clotureAvantInstallation() {
        assertThatThrownBy(() -> service.creer(corps(null, INSTALLATION.minusDays(1), null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("cloture precede");
    }

    @Test
    @DisplayName("le même jour est accepté : un rucher peut ouvrir et fermer le jour même")
    void memeJourAccepte() {
        Ferme f = mock(Ferme.class);
        when(fermes.findById(1L)).thenReturn(Optional.of(f));

        assertThatCode(() -> service.creer(corps(INSTALLATION, INSTALLATION, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("une ferme inconnue dans ce tenant est refusée")
    void fermeInconnue() {
        when(fermes.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creer(corps(null, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("1");
    }

    // ── La création ouvre le premier emplacement ────────────────────────────

    @Test
    @DisplayName("créer un rucher ouvre son PREMIER emplacement, motif « installation »")
    void creationOuvreLEmplacement() {
        Ferme f = mock(Ferme.class);
        when(fermes.findById(1L)).thenReturn(Optional.of(f));

        service.creer(corps(null, null, null));

        // Sans ce premier emplacement, l'historique commencerait au premier
        // déménagement et perdrait l'installation.
        ArgumentCaptor<EmplacementSite> capture =
                ArgumentCaptor.forClass(EmplacementSite.class);
        verify(emplacements).save(capture.capture());
        assertThat(capture.getValue().getMotif()).isEqualTo("installation");
        assertThat(capture.getValue().getDateDebut()).isEqualTo(INSTALLATION);
    }

    @Test
    @DisplayName("l'adresse est débarrassée de ses espaces, une chaîne vide devient nulle")
    void adresseNettoyee() {
        Ferme f = mock(Ferme.class);
        Site s = site(1L, INSTALLATION);
        when(fermes.findById(1L)).thenReturn(Optional.of(f));
        when(sites.findById(1L)).thenReturn(Optional.of(s));

        service.mettreAJour(1L, corps(null, null, null));

        verify(s).setAdresseRue("12 chemin bas");
        verify(s).setVille("Béja");
        verify(s).setCodePostal("9000");
    }

    // ── Le déménagement ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("déménagement")
    class Demenagement {

        private DemenagementCorps vers(LocalDate date, String motif) {
            return new DemenagementCorps(new BigDecimal("36.9"), new BigDecimal("10.4"),
                    null, date, motif, null);
        }

        @Test
        @DisplayName("l'emplacement courant se ferme le jour où le nouveau s'ouvre")
        void fermetureEtOuvertureLeMemeJour() {
            Site s = site(1L, INSTALLATION);
            EmplacementSite courant = mock(EmplacementSite.class);
            when(courant.getDateDebut()).thenReturn(INSTALLATION);
            when(sites.findById(1L)).thenReturn(Optional.of(s));
            when(emplacements.findBySite_IdAndDateFinIsNull(1L)).thenReturn(Optional.of(courant));

            service.demenager(1L, vers(LocalDate.of(2026, 6, 15), "transhumance"));

            // Un rucher n'est jamais à deux endroits, ni nulle part entre les
            // deux : la date de fin de l'un EST la date de début de l'autre.
            verify(courant).setDateFin(LocalDate.of(2026, 6, 15));
            ArgumentCaptor<EmplacementSite> nouveau =
                    ArgumentCaptor.forClass(EmplacementSite.class);
            verify(emplacements).save(nouveau.capture());
            assertThat(nouveau.getValue().getDateDebut()).isEqualTo(LocalDate.of(2026, 6, 15));
        }

        @Test
        @DisplayName("le flush précède l'insertion — sans lui, tout déménagement échouerait")
        void flushAvantInsertion() {
            Site s = site(1L, INSTALLATION);
            EmplacementSite courant = mock(EmplacementSite.class);
            when(courant.getDateDebut()).thenReturn(INSTALLATION);
            when(sites.findById(1L)).thenReturn(Optional.of(s));
            when(emplacements.findBySite_IdAndDateFinIsNull(1L)).thenReturn(Optional.of(courant));

            service.demenager(1L, vers(LocalDate.of(2026, 6, 15), null));

            // Hibernate ordonne ses actions par TYPE : tous les INSERT d'abord,
            // les UPDATE ensuite. Sans ce flush, le nouvel emplacement (date de
            // fin nulle) serait inséré avant que l'ancien ne soit clos, et
            // `uq_emplacement_courant` refuserait deux emplacements ouverts sur
            // le même site — un déménagement échouerait systématiquement, en 500.
            InOrder ordre = inOrder(emplacements);
            ordre.verify(emplacements).flush();
            ordre.verify(emplacements).save(any(EmplacementSite.class));
        }

        @Test
        @DisplayName("un déménagement antérieur à l'emplacement courant est refusé")
        void demenagementRetroactif() {
            Site s = site(1L, INSTALLATION);
            EmplacementSite courant = mock(EmplacementSite.class);
            when(courant.getDateDebut()).thenReturn(LocalDate.of(2026, 6, 15));
            when(sites.findById(1L)).thenReturn(Optional.of(s));
            when(emplacements.findBySite_IdAndDateFinIsNull(1L)).thenReturn(Optional.of(courant));

            // Sinon l'historique porterait deux périodes qui se chevauchent, et
            // « où était le rucher le 1er mai » n'aurait plus de réponse unique.
            assertThatThrownBy(() -> service.demenager(1L, vers(LocalDate.of(2026, 5, 1), null)))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("commence avant");
            verify(emplacements, never()).save(any());
        }

        @Test
        @DisplayName("le même jour que l'emplacement courant est accepté")
        void demenagementLeJourMeme() {
            Site s = site(1L, INSTALLATION);
            EmplacementSite courant = mock(EmplacementSite.class);
            when(courant.getDateDebut()).thenReturn(INSTALLATION);
            when(sites.findById(1L)).thenReturn(Optional.of(s));
            when(emplacements.findBySite_IdAndDateFinIsNull(1L)).thenReturn(Optional.of(courant));

            assertThatCode(() -> service.demenager(1L, vers(INSTALLATION, null)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("sans emplacement courant, le déménagement en ouvre un sans rien fermer")
        void aucunEmplacementCourant() {
            Site s = site(1L, INSTALLATION);
            when(sites.findById(1L)).thenReturn(Optional.of(s));
            when(emplacements.findBySite_IdAndDateFinIsNull(1L)).thenReturn(Optional.empty());

            service.demenager(1L, vers(LocalDate.of(2026, 6, 15), null));

            verify(emplacements, never()).flush();
            verify(emplacements).save(any(EmplacementSite.class));
        }

        @Test
        @DisplayName("le motif par défaut est « transhumance »")
        void motifParDefaut() {
            Site s = site(1L, INSTALLATION);
            when(sites.findById(1L)).thenReturn(Optional.of(s));
            when(emplacements.findBySite_IdAndDateFinIsNull(1L)).thenReturn(Optional.empty());

            service.demenager(1L, vers(LocalDate.of(2026, 6, 15), null));

            ArgumentCaptor<EmplacementSite> capture =
                    ArgumentCaptor.forClass(EmplacementSite.class);
            verify(emplacements).save(capture.capture());
            assertThat(capture.getValue().getMotif()).isEqualTo("transhumance");
        }

        @Test
        @DisplayName("la position du rucher suit celle de son nouvel emplacement")
        void positionDuSiteMiseAJour() {
            Site s = site(1L, INSTALLATION);
            when(sites.findById(1L)).thenReturn(Optional.of(s));
            when(emplacements.findBySite_IdAndDateFinIsNull(1L)).thenReturn(Optional.empty());

            service.demenager(1L, vers(LocalDate.of(2026, 6, 15), null));

            verify(s).setLatitude(new BigDecimal("36.9"));
            verify(s).setLongitude(new BigDecimal("10.4"));
            verify(s).setDateDemenagement(LocalDate.of(2026, 6, 15));
        }
    }

    // ── Les ressources florales ─────────────────────────────────────────────

    @Nested
    @DisplayName("ressources florales")
    class Ressources {

        private RessourceFloraleCorps florale(Integer moisDebut, Integer moisFin) {
            return new RessourceFloraleCorps("colza", 800, moisDebut, moisFin, null);
        }

        @Test
        @DisplayName("une période de floraison demande ses DEUX mois, ou aucun")
        void moisIncomplets() {
            Ferme f = mock(Ferme.class);
            when(fermes.findById(1L)).thenReturn(Optional.of(f));

            // La base le refuserait aussi (`ck_ressource_mois`) ; le dire ici
            // évite l'erreur SQL en 500.
            assertThatThrownBy(() ->
                    service.creer(corps(null, null, List.of(florale(4, null)))))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("son debut ET sa fin");
            assertThatThrownBy(() ->
                    service.creer(corps(null, null, List.of(florale(null, 5)))))
                    .isInstanceOf(RequeteInvalide.class);
        }

        @Test
        @DisplayName("aucun mois est accepté : la ressource est déclarée sans calendrier")
        void aucunMois() {
            Ferme f = mock(Ferme.class);
            when(fermes.findById(1L)).thenReturn(Optional.of(f));

            assertThatCode(() ->
                    service.creer(corps(null, null, List.of(florale(null, null)))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("les deux mois ensemble sont posés sur la ressource")
        void deuxMois() {
            Ferme f = mock(Ferme.class);
            when(fermes.findById(1L)).thenReturn(Optional.of(f));

            service.creer(corps(null, null, List.of(florale(4, 5))));

            ArgumentCaptor<RessourceFlorale> capture =
                    ArgumentCaptor.forClass(RessourceFlorale.class);
            verify(ressources).save(capture.capture());
            assertThat(capture.getValue().getMoisDebut()).isEqualTo(4);
            assertThat(capture.getValue().getMoisFin()).isEqualTo(5);
        }

        @Test
        @DisplayName("la liste reçue est la liste FINALE : le vidage précède l'insertion")
        void remplacementEnBloc() {
            Ferme f = mock(Ferme.class);
            Site s = site(1L, INSTALLATION);
            when(fermes.findById(1L)).thenReturn(Optional.of(f));
            when(sites.findById(1L)).thenReturn(Optional.of(s));

            service.mettreAJour(1L, corps(null, null, List.of(florale(4, 5))));

            // `uq_ressource_site` refuse deux fois la même ressource sur un site,
            // y compris entre l'ancienne liste et la nouvelle : le vidage doit
            // être poussé en base avant la première insertion.
            InOrder ordre = inOrder(ressources);
            ordre.verify(ressources).deleteBySite_Id(1L);
            ordre.verify(ressources).flush();
            ordre.verify(ressources).save(any(RessourceFlorale.class));
        }

        @Test
        @DisplayName("une liste ABSENTE ne touche pas aux ressources existantes")
        void listeAbsente() {
            Ferme f = mock(Ferme.class);
            Site s = site(1L, INSTALLATION);
            when(fermes.findById(1L)).thenReturn(Optional.of(f));
            when(sites.findById(1L)).thenReturn(Optional.of(s));

            service.mettreAJour(1L, corps(null, null, null));

            // Absente n'est pas vide : une mise à jour qui ne parle pas des
            // ressources ne doit pas les effacer.
            verify(ressources, never()).deleteBySite_Id(any());
        }

        @Test
        @DisplayName("une liste VIDE efface les ressources")
        void listeVide() {
            Ferme f = mock(Ferme.class);
            Site s = site(1L, INSTALLATION);
            when(fermes.findById(1L)).thenReturn(Optional.of(f));
            when(sites.findById(1L)).thenReturn(Optional.of(s));

            service.mettreAJour(1L, corps(null, null, List.of()));

            verify(ressources).deleteBySite_Id(1L);
            verify(ressources, never()).save(any());
        }
    }

    // ── Historique et lectures ──────────────────────────────────────────────

    @Test
    @DisplayName("l'historique des emplacements sort MASQUÉ, comme la position courante")
    void historiqueMasque() {
        Site s = site(1L, INSTALLATION);
        EmplacementSite e = mock(EmplacementSite.class);
        when(e.getSite()).thenReturn(s);
        when(e.getLatitude()).thenReturn(LAT);
        when(e.getLongitude()).thenReturn(LON);
        when(e.getDateDebut()).thenReturn(INSTALLATION);
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(emplacements.findBySite_IdOrderByDateDebutDescIdDesc(1L)).thenReturn(List.of(e));

        var historique = service.historique(1L);

        // La suite des emplacements dit aussi où le rucher se trouvait quand
        // personne ne le surveillait : la raison de masquer est plus forte ici
        // que sur la position courante.
        assertThat(historique).hasSize(1);
        assertThat(historique.get(0).latitude()).isEqualByComparingTo("36.81");
        // L'altitude ne sort qu'au rôle autorisé : elle situe autant qu'une
        // coordonnée en terrain accidenté.
        assertThat(historique.get(0).altitude()).isNull();
    }

    @Test
    @DisplayName("un rucher inconnu est refusé en 404 sur toutes les lectures")
    void siteIntrouvable() {
        when(sites.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.historique(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.demenager(999L,
                new DemenagementCorps(LAT, LON, null, INSTALLATION, null, null)))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt")
    void suppression() {
        Site s = site(1L, INSTALLATION);
        when(sites.findById(1L)).thenReturn(Optional.of(s));

        service.supprimer(1L);

        verify(sites).delete(s);
    }
}
