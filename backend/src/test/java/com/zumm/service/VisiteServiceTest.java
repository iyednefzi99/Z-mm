package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.ObservationPathologie;
import com.zumm.domain.Photo;
import com.zumm.domain.PointObservation;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.ObservationPathologieRepository;
import com.zumm.repository.PhotoRepository;
import com.zumm.repository.PlanningRepository;
import com.zumm.repository.PointObservationRepository;
import com.zumm.repository.ReleveObservationRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.ConflitVersion;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MeteoVisite;
import com.zumm.web.dto.ObservationVisite;
import com.zumm.web.dto.PathologieCorps;
import com.zumm.web.dto.PhotoCorps;
import com.zumm.web.dto.PointReleve;
import com.zumm.web.dto.VisiteCorps;
import com.zumm.web.dto.VisiteReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Réalisation des visites (US-009/010, lot 3 du plan de couverture).
 *
 * <p>88,2 % d'instructions, 82,4 % de branches. Ce qui manquait n'est pas la
 * saisie nominale — les tests d'intégration la couvrent — mais les <em>trois
 * règles</em> qui décident de ce que le registre retient :
 *
 * <ol>
 *   <li><strong>{@code null} efface.</strong> Le corps décrit l'état complet de
 *       la visite après la requête. Une mise à jour qui omet la grille ne doit
 *       pas conserver l'ancienne : elle serait alors attribuée à une inspection
 *       qui ne l'a pas faite.</li>
 *   <li><strong>Remplacement, jamais fusion</strong>, pour les pathologies comme
 *       pour les relevés. Une fusion rendrait impossible de RETIRER une
 *       suspicion saisie par erreur — et la statistique sanitaire ne
 *       redescendrait jamais.</li>
 *   <li><strong>La garde de version compare à la SECONDE.</strong> PostgreSQL
 *       rend un {@code timestamptz} à la microseconde, que JSON ne restitue pas
 *       toujours à l'identique : comparer les instants bruts produirait des
 *       conflits fantômes sur une valeur pourtant relue telle quelle.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class VisiteServiceTest {

    @Mock private VisiteRepository visites;
    @Mock private PhotoRepository photos;
    @Mock private RucheRepository ruches;
    @Mock private AgentRepository agents;
    @Mock private PlanningRepository plannings;
    @Mock private ObservationPathologieRepository pathologies;
    @Mock private ReleveObservationRepository releves;
    @Mock private PointObservationRepository points;

    private VisiteService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 5, 20);

    @BeforeEach
    void monter() {
        service = new VisiteService(visites, photos, ruches, agents, plannings,
                pathologies, releves, points);
        lenient().when(visites.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(photos.findByVisiteIdOrderByIdAsc(any())).thenReturn(List.of());
        lenient().when(pathologies.findByVisite_IdOrderByPathologieAsc(any()))
                .thenReturn(List.of());
        lenient().when(releves.findByIdVisiteIdOrderByIdPointCodeAsc(any())).thenReturn(List.of());
        lenient().when(pathologies.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        lenient().when(releves.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    private static Ruche ruche() {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(5L);
        lenient().when(r.getModele()).thenReturn("Dadant 10");
        return r;
    }

    private static Agent agent() {
        Agent a = mock(Agent.class);
        lenient().when(a.getId()).thenReturn(9L);
        lenient().when(a.getNom()).thenReturn("Nefzi");
        return a;
    }

    /** Un point du référentiel fermé : la table n'a aucun constructeur public. */
    private static PointObservation point(String code, boolean echelle) {
        PointObservation p = mock(PointObservation.class);
        lenient().when(p.getCode()).thenReturn(code);
        lenient().when(p.estEchelle()).thenReturn(echelle);
        lenient().when(p.getLibelle()).thenReturn("Libellé de " + code);
        return p;
    }

    private void tenantComplet() {
        Optional<Ruche> r = Optional.of(ruche());
        Optional<Agent> a = Optional.of(agent());
        when(ruches.findById(5L)).thenReturn(r);
        when(agents.findById(9L)).thenReturn(a);
    }

    private static VisiteCorps corps(ObservationVisite observation, MeteoVisite meteo,
            List<PathologieCorps> patho, List<PointReleve> releves) {
        return new VisiteCorps(5L, 9L, null, JOUR, null, 30, null,
                "RAS", null, null, null, null, null, null, observation, meteo, patho, releves);
    }

    // ── Le rattachement ─────────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche, un agent ou un planning hors tenant est refusé en 400")
    void rattachementsInconnus() {
        when(ruches.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.creer(corps(null, null, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Ruche inconnue dans ce tenant : 5");

        Optional<Ruche> presente = Optional.of(ruche());
        when(ruches.findById(5L)).thenReturn(presente);
        when(agents.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.creer(corps(null, null, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Agent inconnu dans ce tenant : 9");
    }

    @Test
    @DisplayName("un planning absent est accepté ; un planning inconnu est refusé")
    void planning() {
        tenantComplet();
        assertThat(service.creer(corps(null, null, null, null))).isNotNull();
        verify(plannings, never()).findById(any());

        when(plannings.findById(4L)).thenReturn(Optional.empty());
        VisiteCorps avecPlanning = new VisiteCorps(5L, 9L, 4L, JOUR, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.creer(avecPlanning))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Planning inconnu dans ce tenant : 4");
    }

    @Test
    @DisplayName("sans raison, la visite est un CONTRÔLE, jamais une raison nulle")
    void raisonParDefaut() {
        tenantComplet();

        // La colonne est NOT NULL : sans ce défaut, la moitié des saisies
        // rapides échoueraient sur une contrainte plutôt que de s'enregistrer.
        assertThat(service.creer(corps(null, null, null, null)).raison())
                .isEqualTo(RaisonVisite.CONTROLE);
    }

    @Test
    @DisplayName("une raison nulle en MODIFICATION conserve l'ancienne")
    void raisonNullePreserveeALaModification() {
        Visite visite = new Visite(ruche(), agent(), JOUR, RaisonVisite.RECOLTE);
        Optional<Visite> presente = Optional.of(visite);
        when(visites.findById(1L)).thenReturn(presente);
        tenantComplet();

        // Seul champ où `null` ne veut PAS dire « efface » : il n'existe pas de
        // visite sans raison, et remettre CONTROLE réécrirait une récolte en
        // contrôle à chaque modification de commentaire.
        service.mettreAJour(1L, corps(null, null, null, null), null);

        assertThat(visite.getRaison()).isEqualTo(RaisonVisite.RECOLTE);
    }

    // ── `null` efface ───────────────────────────────────────────────────────

    @Test
    @DisplayName("une observation absente EFFACE la grille, elle ne la conserve pas")
    void observationNulleEfface() {
        Visite visite = new Visite(ruche(), agent(), JOUR, RaisonVisite.CONTROLE);
        visite.setCadresCouvain(6);
        visite.setReineVue(true);
        visite.setTemperament("doux");
        Optional<Visite> presente = Optional.of(visite);
        when(visites.findById(1L)).thenReturn(presente);
        tenantComplet();

        service.mettreAJour(1L, corps(null, null, null, null), null);

        // Conserver l'ancienne grille l'attribuerait à une inspection qui ne
        // l'a pas faite — c'est le corps qui décrit l'état complet après la
        // requête, comme pour `constatations`.
        assertThat(visite.getCadresCouvain()).isNull();
        assertThat(visite.getReineVue()).isNull();
        assertThat(visite.getTemperament()).isNull();
    }

    @Test
    @DisplayName("une météo absente efface la météo figée, pour la même raison")
    void meteoNulleEfface() {
        Visite visite = new Visite(ruche(), agent(), JOUR, RaisonVisite.CONTROLE);
        visite.setMeteoTemperatureC(new BigDecimal("24.5"));
        visite.setMeteoSource("open-meteo");
        Optional<Visite> presente = Optional.of(visite);
        when(visites.findById(1L)).thenReturn(presente);
        tenantComplet();

        service.mettreAJour(1L, corps(null, null, null, null), null);

        assertThat(visite.getMeteoTemperatureC()).isNull();
        assertThat(visite.getMeteoSource()).isNull();
    }

    @Test
    @DisplayName("une observation fournie est appliquée telle quelle")
    void observationAppliquee() {
        tenantComplet();
        ObservationVisite grille = new ObservationVisite(true, true, false, "compact",
                true, 0, null, 6, 4, 2, "doux");

        VisiteReponse reponse = service.creer(corps(grille, null, null, null));

        assertThat(reponse.observation()).isNotNull();
        assertThat(reponse.observation().cadresCouvain()).isEqualTo(6);
        assertThat(reponse.observation().motifPonte()).isEqualTo("compact");
    }

    @Test
    @DisplayName("une météo fournie est figée sur la visite avec sa source")
    void meteoAppliquee() {
        tenantComplet();
        MeteoVisite meteo = new MeteoVisite(new BigDecimal("24.5"), 60,
                new BigDecimal("12"), "saisie");

        // La source est ce qui distingue un relevé d'une simulation : sans elle,
        // une corrélation météo × production ne saurait pas ce qu'elle corrèle.
        assertThat(service.creer(corps(null, meteo, null, null)).meteo().source())
                .isEqualTo("saisie");
    }

    // ── Remplacement, jamais fusion ─────────────────────────────────────────

    @Nested
    class Pathologies {

        @Test
        @DisplayName("les anciennes sont supprimées AVANT que les nouvelles soient écrites")
        void remplacementComplet() {
            tenantComplet();
            ObservationPathologie ancienne = mock(ObservationPathologie.class);
            List<ObservationPathologie> anciennes = List.of(ancienne);
            when(pathologies.findByVisite_IdOrderByPathologieAsc(any())).thenReturn(anciennes);

            service.creer(corps(null, null,
                    List.of(new PathologieCorps("varroose", "moderee", null)), null));

            InOrder ordre = inOrder(pathologies);
            ordre.verify(pathologies).deleteAll(anciennes);
            ordre.verify(pathologies).saveAll(anyList());
        }

        @Test
        @DisplayName("une liste vide efface tout sans écrire : une suspicion s'infirme")
        void listeVideEfface() {
            tenantComplet();

            // Une fusion rendrait impossible de retirer une pathologie saisie
            // par erreur, et la statistique sanitaire ne redescendrait jamais.
            service.creer(corps(null, null, List.of(), null));

            verify(pathologies).deleteAll(anyList());
            verify(pathologies, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("une gravité absente vaut « suspectée », le plus faible des quatre")
        void graviteParDefaut() {
            tenantComplet();

            // Choisir un défaut plus élevé ferait monter la statistique
            // sanitaire du parc sur des saisies incomplètes.
            VisiteReponse r = service.creer(corps(null, null,
                    List.of(new PathologieCorps("varroose", null, null)), null));

            assertThat(r.pathologies()).singleElement()
                    .satisfies(p -> assertThat(p.gravite()).isEqualTo("suspectee"));
        }
    }

    // ── Les relevés du carnet paramétrable ──────────────────────────────────

    @Nested
    class Releves {

        @Test
        @DisplayName("un flush() sépare la suppression de l'écriture")
        void flushEntreLesDeux() {
            tenantComplet();
            // Le point est construit AVANT le stubbing : `point(...)` stube un
            // mock, et le faire pendant qu'un `when(...)` est ouvert casse
            // Mockito en `UnfinishedStubbing`.
            PointObservation couvainPlat = point("couvain_plat", false);
            when(points.findAllById(anyList())).thenReturn(List.of(couvainPlat));

            service.creer(corps(null, null, null,
                    List.of(new PointReleve("couvain_plat", true, null))));

            // Hibernate ordonne ses actions PAR TYPE : sans ce flush, les
            // insertions partiraient avant les suppressions et la clé primaire
            // (visite, point) refuserait une réécriture pourtant légitime.
            InOrder ordre = inOrder(releves);
            ordre.verify(releves).deleteAll(anyList());
            ordre.verify(releves).flush();
            ordre.verify(releves).saveAll(anyList());
        }

        @Test
        @DisplayName("une liste vide n'appelle même pas flush()")
        void listeVideNeFlushPas() {
            tenantComplet();

            service.creer(corps(null, null, null, List.of()));

            verify(releves).deleteAll(anyList());
            verify(releves, never()).flush();
            verify(releves, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("un code hors référentiel est refusé en 400 qui le nomme")
        void codeInconnu() {
            tenantComplet();
            when(points.findAllById(anyList())).thenReturn(List.of());

            // Le référentiel est fermé (la V28 retire INSERT au rôle applicatif) :
            // un code inconnu est une faute de frappe du client, pas une panne.
            assertThatThrownBy(() -> service.creer(corps(null, null, null,
                    List.of(new PointReleve("inexistant", true, null)))))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("Point d'observation inconnu : inexistant");
        }

        @Test
        @DisplayName("un point à ÉCHELLE refuse une case cochée, et réciproquement")
        void correspondanceValeurType() {
            tenantComplet();
            List<PointObservation> deux =
                    List.of(point("intensite", true), point("presence", false));
            when(points.findAllById(anyList())).thenReturn(deux);

            // La base ne peut pas le vérifier — le type vit dans une AUTRE table,
            // hors de portée d'un CHECK — et l'accepter silencieusement
            // produirait des colonnes à moitié remplies dans toute statistique.
            assertThatThrownBy(() -> service.creer(corps(null, null, null,
                    List.of(new PointReleve("intensite", true, null)))))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("intensite de 0 a 3");

            assertThatThrownBy(() -> service.creer(corps(null, null, null,
                    List.of(new PointReleve("presence", null, 2)))))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("case cochee");
        }

        @Test
        @DisplayName("les DEUX valeurs à la fois sont refusées, sur l'un comme sur l'autre type")
        void deuxValeursALaFois() {
            tenantComplet();
            List<PointObservation> deux =
                    List.of(point("intensite", true), point("presence", false));
            when(points.findAllById(anyList())).thenReturn(deux);

            // « coché ET niveau 2 » ne veut rien dire : la contrainte de la V28
            // n'autorise qu'une colonne renseignée par ligne.
            assertThatThrownBy(() -> service.creer(corps(null, null, null,
                    List.of(new PointReleve("intensite", true, 2)))))
                    .isInstanceOf(RequeteInvalide.class);
            assertThatThrownBy(() -> service.creer(corps(null, null, null,
                    List.of(new PointReleve("presence", true, 2)))))
                    .isInstanceOf(RequeteInvalide.class);
        }

        @Test
        @DisplayName("un relevé valide de chaque type est écrit dans la bonne colonne")
        void relevesValides() {
            tenantComplet();
            List<PointObservation> deux =
                    List.of(point("intensite", true), point("presence", false));
            when(points.findAllById(anyList())).thenReturn(deux);

            VisiteReponse r = service.creer(corps(null, null, null,
                    List.of(new PointReleve("intensite", null, 2),
                            new PointReleve("presence", false, null))));

            assertThat(r.points()).extracting(PointReleve::code)
                    .containsExactlyInAnyOrder("intensite", "presence");
            assertThat(r.points()).filteredOn(p -> "presence".equals(p.code()))
                    .singleElement()
                    .satisfies(p -> {
                        // « regardé, absent » n'est pas « pas regardé » : false
                        // se conserve, il ne se réduit pas à une ligne manquante.
                        assertThat(p.coche()).isFalse();
                        assertThat(p.niveau()).isNull();
                    });
        }

        @Test
        @DisplayName("un doublon dans la requête est dédoublonné, pas rejeté")
        void doublonDedoublonne() {
            tenantComplet();
            PointObservation presence = point("presence", false);
            when(points.findAllById(anyList())).thenReturn(List.of(presence));
            PointReleve deuxFois = new PointReleve("presence", true, null);

            // Deux fois la même case dans un corps est une répétition, pas une
            // contradiction ; la laisser passer heurterait la clé primaire.
            assertThat(service.creer(corps(null, null, null, List.of(deuxFois, deuxFois)))
                    .points()).hasSize(1);
        }
    }

    // ── La garde de version ─────────────────────────────────────────────────

    @Nested
    class GardeDeVersion {

        private Visite visiteModifieeLe(Instant majLe) {
            // Les deux mocks portes par la visite sont construits AVANT d'ouvrir
            // le moindre stubbing. Appeler `ruche()` a l'interieur d'un
            // `thenReturn(...)` fait creer et stuber un mock pendant qu'un autre
            // stubbing est en cours : Mockito le refuse en `UnfinishedStubbing`,
            // et l'erreur pointe la ligne suivante, jamais la cause.
            Ruche laRuche = ruche();
            Agent lAgent = agent();
            Visite v = mock(Visite.class);
            lenient().when(v.getId()).thenReturn(1L);
            lenient().when(v.getMajLe()).thenReturn(majLe);
            lenient().when(v.getRuche()).thenReturn(laRuche);
            lenient().when(v.getAgent()).thenReturn(lAgent);
            lenient().when(v.getDateVisite()).thenReturn(JOUR);
            lenient().when(v.getRaison()).thenReturn(RaisonVisite.CONTROLE);
            return v;
        }

        @Test
        @DisplayName("une version attendue nulle DÉSACTIVE la garde")
        void versionNulleNeGardeRien() {
            Optional<Visite> presente =
                    Optional.of(visiteModifieeLe(Instant.parse("2026-05-20T10:00:00Z")));
            when(visites.findById(1L)).thenReturn(presente);
            tenantComplet();

            // Les écrans en ligne modifient ce qu'ils viennent de lire ; leur
            // imposer un en-tête supplémentaire n'aurait protégé personne.
            assertThatCode(() -> service.mettreAJour(1L, corps(null, null, null, null), null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("une version serveur plus récente refuse en 409 et rend SA version")
        void versionPerimee() {
            Instant serveur = Instant.parse("2026-05-20T10:05:00Z");
            Optional<Visite> presente = Optional.of(visiteModifieeLe(serveur));
            when(visites.findById(1L)).thenReturn(presente);

            // C'est le rejeu de la file HORS LIGNE qui en a besoin : une saisie
            // redescendue du rucher à plusieurs heures de retard, et deux agents
            // peuvent l'avoir faite.
            assertThatThrownBy(() -> service.mettreAJour(1L, corps(null, null, null, null),
                    Instant.parse("2026-05-20T10:00:00Z")))
                    .isInstanceOf(ConflitVersion.class)
                    .hasMessageContaining("2026-05-20T10:05:00Z");
        }

        @Test
        @DisplayName("la comparaison est tronquée À LA SECONDE : pas de conflit fantôme")
        void microsecondesIgnorees() {
            Optional<Visite> presente =
                    Optional.of(visiteModifieeLe(Instant.parse("2026-05-20T10:00:00.123456Z")));
            when(visites.findById(1L)).thenReturn(presente);
            tenantComplet();

            // PostgreSQL rend un timestamptz à la microseconde, que JSON ne
            // restitue pas toujours à l'identique. Comparer les instants bruts
            // ferait échouer un rejeu sur une valeur pourtant relue telle quelle.
            assertThatCode(() -> service.mettreAJour(1L, corps(null, null, null, null),
                    Instant.parse("2026-05-20T10:00:00Z")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("une visite jamais modifiée ne peut pas être en conflit")
        void majLeNul() {
            Optional<Visite> presente = Optional.of(visiteModifieeLe(null));
            when(visites.findById(1L)).thenReturn(presente);
            tenantComplet();

            assertThatCode(() -> service.mettreAJour(1L, corps(null, null, null, null),
                    Instant.parse("2026-05-20T10:00:00Z")))
                    .doesNotThrowAnyException();
        }
    }

    // ── Photos ──────────────────────────────────────────────────────────────

    @Nested
    class Photos {

        /** Une visite dont l identifiant compte : il n est pas assignable autrement. */
        private Visite visite(long id) {
            Visite v = mock(Visite.class);
            lenient().when(v.getId()).thenReturn(id);
            return v;
        }

        @Test
        @DisplayName("une photo d'une AUTRE visite est refusée en 400, pas supprimée")
        void photoDuneAutreVisite() {
            Visite autre = visite(2L);
            Photo photo = mock(Photo.class);
            when(photo.getVisite()).thenReturn(autre);
            Optional<Photo> presente = Optional.of(photo);
            when(photos.findById(7L)).thenReturn(presente);

            // Sans ce contrôle, connaître un identifiant de photo suffirait à la
            // supprimer depuis n'importe quelle visite du tenant.
            assertThatThrownBy(() -> service.supprimerPhoto(1L, 7L))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("n'appartient pas à la visite 1");
            verify(photos, never()).delete(any());
        }

        @Test
        @DisplayName("une photo de la bonne visite est supprimée")
        void photoSupprimee() {
            Visite bonne = visite(1L);
            Photo photo = mock(Photo.class);
            when(photo.getVisite()).thenReturn(bonne);
            Optional<Photo> presente = Optional.of(photo);
            when(photos.findById(7L)).thenReturn(presente);

            service.supprimerPhoto(1L, 7L);

            verify(photos).delete(photo);
        }

        @Test
        @DisplayName("une photo inconnue est refusée en 404")
        void photoIntrouvable() {
            when(photos.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supprimerPhoto(1L, 999L))
                    .isInstanceOf(RessourceIntrouvable.class);
        }

        @Test
        @DisplayName("lister les photos d'une visite inconnue est un 404, pas une liste vide")
        void listerSurVisiteInconnue() {
            when(visites.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.listerPhotos(999L))
                    .isInstanceOf(RessourceIntrouvable.class);
            assertThatThrownBy(() -> service.ajouterPhoto(999L, new PhotoCorps("/p.jpg", null)))
                    .isInstanceOf(RessourceIntrouvable.class);
        }

        @Test
        @DisplayName("ajouter une photo la rattache à la visite résolue")
        void ajout() {
            Visite v = new Visite(ruche(), agent(), JOUR, RaisonVisite.CONTROLE);
            Optional<Visite> presente = Optional.of(v);
            when(visites.findById(1L)).thenReturn(presente);
            when(photos.save(any())).thenAnswer(i -> i.getArgument(0));

            assertThat(service.ajouterPhoto(1L, new PhotoCorps("/photos/1.jpg", "Cadre 3")).url())
                    .isEqualTo("/photos/1.jpg");
        }
    }

    // ── Lectures et suppression ─────────────────────────────────────────────

    @Test
    @DisplayName("la liste recharge photos, pathologies et relevés pour CHAQUE visite")
    void liste() {
        Visite v = new Visite(ruche(), agent(), JOUR, RaisonVisite.CONTROLE);
        when(visites.findAll()).thenReturn(List.of(v));

        assertThat(service.lister()).hasSize(1);
        verify(photos).findByVisiteIdOrderByIdAsc(any());
        verify(pathologies).findByVisite_IdOrderByPathologieAsc(any());
        verify(releves).findByIdVisiteIdOrderByIdPointCodeAsc(any());
    }

    @Test
    @DisplayName("une visite inconnue est refusée en 404 sur les trois chemins")
    void visiteIntrouvable() {
        when(visites.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(999L)).isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L)).isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() ->
                service.mettreAJour(999L, corps(null, null, null, null), null))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt")
    void suppression() {
        Visite v = new Visite(ruche(), agent(), JOUR, RaisonVisite.CONTROLE);
        Optional<Visite> presente = Optional.of(v);
        when(visites.findById(1L)).thenReturn(presente);

        service.supprimer(1L);

        verify(visites).delete(v);
    }

    @Test
    @DisplayName("obtenir rend la visite avec ses trois collections")
    void obtenir() {
        Visite v = new Visite(ruche(), agent(), JOUR, RaisonVisite.CONTROLE);
        Optional<Visite> presente = Optional.of(v);
        when(visites.findById(1L)).thenReturn(presente);

        assertThat(service.obtenir(1L)).isNotNull();
        verify(photos).findByVisiteIdOrderByIdAsc(any());
        verify(pathologies).findByVisite_IdOrderByPathologieAsc(any());
        verify(releves).findByIdVisiteIdOrderByIdPointCodeAsc(any());
    }
}
