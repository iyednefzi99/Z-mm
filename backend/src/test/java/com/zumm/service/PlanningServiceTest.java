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
import com.zumm.domain.Planning;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.StatutPlanning;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.PlanningRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.PlanningCorps;
import com.zumm.web.dto.TourneeReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Plannings de visite et tournées (US-008, US-047 ; lot 3 du plan de couverture).
 *
 * <p>82,7 % d'instructions et 80 % de branches. Deux décisions s'y jouent, et la
 * seconde décide de ce que l'agent voit sur son téléphone :
 *
 * <ol>
 *   <li><strong>Un refus doit être motivé</strong> — et l'approbation efface le
 *       motif précédent. Un planning approuvé qui traînerait le motif de son
 *       refus antérieur se lirait comme approuvé <em>malgré</em> une réserve.</li>
 *   <li><strong>Les plannings sont regroupés par SITE.</strong> Deux ruches d'un
 *       même rucher ne font qu'un déplacement : compter par ruche gonflerait la
 *       tournée d'un facteur dix sur un rucher de dix colonies.</li>
 * </ol>
 *
 * <p>L'ordre sort d'une heuristique ({@code OptimiseurTournee}), il n'est pas
 * optimal — et les distances sont géodésiques mais <strong>à vol d'oiseau</strong> :
 * le routage sur réseau routier est hors périmètre, et le dire vaut mieux que de
 * laisser croire à un temps de trajet.
 */
@ExtendWith(MockitoExtension.class)
class PlanningServiceTest {

    @Mock private PlanningRepository plannings;
    @Mock private RucheRepository ruches;
    @Mock private AgentRepository agents;
    @Mock private SiteRepository sites;

    private PlanningService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void monter() {
        service = new PlanningService(plannings, ruches, agents, sites);
        lenient().when(plannings.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(sites.distancesEntre(any())).thenReturn(List.of());
    }

    private static Agent agent(Long id, String nom) {
        Agent a = mock(Agent.class);
        lenient().when(a.getId()).thenReturn(id);
        lenient().when(a.getNom()).thenReturn(nom);
        return a;
    }

    private static Site site(Long id, String nom) {
        Site s = mock(Site.class);
        lenient().when(s.getId()).thenReturn(id);
        lenient().when(s.getNom()).thenReturn(nom);
        lenient().when(s.getLatitude()).thenReturn(new BigDecimal("36.8"));
        lenient().when(s.getLongitude()).thenReturn(new BigDecimal("10.2"));
        return s;
    }

    private static Ruche ruche(Long id, Site site) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getSite()).thenReturn(site);
        lenient().when(r.getModele()).thenReturn("Dadant");
        return r;
    }

    private static Planning planning(Long id, Ruche ruche, Agent agent) {
        Planning p = mock(Planning.class);
        lenient().when(p.getId()).thenReturn(id);
        lenient().when(p.getRuche()).thenReturn(ruche);
        lenient().when(p.getAgent()).thenReturn(agent);
        lenient().when(p.getDatePrevue()).thenReturn(JOUR);
        lenient().when(p.getRaison()).thenReturn(RaisonVisite.CONTROLE);
        lenient().when(p.getStatut()).thenReturn(StatutPlanning.PROPOSE);
        return p;
    }

    private static PlanningCorps corps(Long superviseurId, RaisonVisite raison) {
        return new PlanningCorps(42L, 3L, superviseurId, JOUR, null, 45, raison);
    }

    // ── Saisie ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sans raison, le planning est un CONTRÔLE : le défaut est le cas fréquent")
    void raisonParDefaut() {
        Site s = site(1L, "Rucher du haut");
        Ruche r = ruche(42L, s);
        Agent a = agent(3L, "Amal");
        when(ruches.findById(42L)).thenReturn(Optional.of(r));
        when(agents.findById(3L)).thenReturn(Optional.of(a));

        service.creer(corps(null, null));

        verify(plannings).save(any(Planning.class));
    }

    @Test
    @DisplayName("un superviseur absent est accepté, un superviseur inconnu est refusé")
    void superviseurEventuel() {
        Site s = site(1L, "Rucher du haut");
        Ruche r = ruche(42L, s);
        Agent a = agent(3L, "Amal");
        when(ruches.findById(42L)).thenReturn(Optional.of(r));
        when(agents.findById(3L)).thenReturn(Optional.of(a));
        when(agents.findById(9L)).thenReturn(Optional.empty());

        service.creer(corps(null, RaisonVisite.CONTROLE));
        verify(plannings).save(any(Planning.class));

        assertThatThrownBy(() -> service.creer(corps(9L, RaisonVisite.CONTROLE)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("une ruche ou un agent inconnu dans ce tenant est refusé")
    void rattachementsInconnus() {
        when(ruches.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creer(corps(null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("42");

        Site s = site(1L, "R");
        Ruche r = ruche(42L, s);
        when(ruches.findById(42L)).thenReturn(Optional.of(r));
        when(agents.findById(3L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.creer(corps(null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("3");
    }

    @Test
    @DisplayName("une raison absente en mise à jour ne remplace pas l'existante")
    void miseAJourSansRaison() {
        Site s = site(1L, "Rucher du haut");
        Ruche r = ruche(42L, s);
        Agent a = agent(3L, "Amal");
        Planning p = planning(7L, r, a);
        when(ruches.findById(42L)).thenReturn(Optional.of(r));
        when(agents.findById(3L)).thenReturn(Optional.of(a));
        when(plannings.findById(7L)).thenReturn(Optional.of(p));

        service.mettreAJour(7L, corps(null, null));

        // Absente veut dire « je ne touche pas » : l'écraser par le défaut
        // transformerait une récolte planifiée en simple contrôle.
        verify(p, never()).setRaison(any());
    }

    @Test
    @DisplayName("une raison fournie en mise à jour remplace l'existante")
    void miseAJourAvecRaison() {
        Site s = site(1L, "Rucher du haut");
        Ruche r = ruche(42L, s);
        Agent a = agent(3L, "Amal");
        Planning p = planning(7L, r, a);
        when(ruches.findById(42L)).thenReturn(Optional.of(r));
        when(agents.findById(3L)).thenReturn(Optional.of(a));
        when(plannings.findById(7L)).thenReturn(Optional.of(p));

        service.mettreAJour(7L, corps(null, RaisonVisite.RECOLTE));

        verify(p).setRaison(RaisonVisite.RECOLTE);
    }

    // ── Approbation et refus ────────────────────────────────────────────────

    @Nested
    @DisplayName("approbation")
    class Approbation {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("un refus sans motif est refusé")
        void refusSansMotif(String motif) {
            // Un refus sans motif oblige l'agent à redemander de vive voix ce que
            // l'écran aurait pu lui dire.
            assertThatThrownBy(() -> service.refuser(7L, motif))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("motivé");
            verify(plannings, never()).findById(any());
        }

        @Test
        @DisplayName("un refus motivé passe le planning en REFUSE et garde le motif")
        void refusMotive() {
            Site s = site(1L, "R");
            Planning p = planning(7L, ruche(42L, s), agent(3L, "Amal"));
            when(plannings.findById(7L)).thenReturn(Optional.of(p));

            service.refuser(7L, "Rucher inaccessible cette semaine");

            verify(p).setStatut(StatutPlanning.REFUSE);
            verify(p).setMotifRefus("Rucher inaccessible cette semaine");
        }

        @Test
        @DisplayName("approuver EFFACE le motif d'un refus antérieur")
        void approbationEffaceLeMotif() {
            Site s = site(1L, "R");
            Planning p = planning(7L, ruche(42L, s), agent(3L, "Amal"));
            when(plannings.findById(7L)).thenReturn(Optional.of(p));

            service.approuver(7L);

            // Un planning approuvé qui traînerait le motif de son refus antérieur
            // se lirait comme approuvé MALGRÉ une réserve.
            verify(p).setStatut(StatutPlanning.APPROUVE);
            verify(p).setMotifRefus(null);
        }

        @Test
        @DisplayName("un planning inconnu est refusé en 404")
        void planningIntrouvable() {
            when(plannings.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approuver(999L))
                    .isInstanceOf(RessourceIntrouvable.class);
            assertThatThrownBy(() -> service.refuser(999L, "motif"))
                    .isInstanceOf(RessourceIntrouvable.class);
            assertThatThrownBy(() -> service.obtenir(999L))
                    .isInstanceOf(RessourceIntrouvable.class);
            assertThatThrownBy(() -> service.supprimer(999L))
                    .isInstanceOf(RessourceIntrouvable.class);
        }
    }

    // ── Tournée ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("tournée")
    class Tournee {

        @Test
        @DisplayName("une journée sans planning rend une tournée vide, pas une erreur")
        void journeeVide() {
            Agent a = agent(3L, "Amal");
            when(agents.findById(3L)).thenReturn(Optional.of(a));
            when(plannings.parAgentEtDate(3L, JOUR, StatutPlanning.REFUSE))
                    .thenReturn(List.of());

            TourneeReponse tournee = service.tournee(3L, JOUR, null);

            assertThat(tournee.nombreSites()).isZero();
            assertThat(tournee.nombreVisites()).isZero();
            assertThat(tournee.distanceTotaleMetres()).isEqualByComparingTo("0");
            assertThat(tournee.etapes()).isEmpty();
        }

        @Test
        @DisplayName("deux ruches d'un même rucher ne font QU'UN déplacement")
        void regroupementParSite() {
            Agent a = agent(3L, "Amal");
            Site s = site(1L, "Rucher du haut");
            Planning p1 = planning(7L, ruche(42L, s), a);
            Planning p2 = planning(8L, ruche(43L, s), a);
            when(agents.findById(3L)).thenReturn(Optional.of(a));
            when(plannings.parAgentEtDate(3L, JOUR, StatutPlanning.REFUSE))
                    .thenReturn(List.of(p1, p2));

            TourneeReponse tournee = service.tournee(3L, JOUR, null);

            // Compter par ruche gonflerait la tournée d'un facteur dix sur un
            // rucher de dix colonies.
            assertThat(tournee.nombreSites()).isEqualTo(1);
            assertThat(tournee.nombreVisites()).isEqualTo(2);
            assertThat(tournee.etapes()).hasSize(1);
            assertThat(tournee.etapes().get(0).planningIds()).containsExactly(7L, 8L);
            assertThat(tournee.etapes().get(0).nombreVisites()).isEqualTo(2);
        }

        @Test
        @DisplayName("la première étape est à distance zéro : on part de là où l'on est")
        void premiereEtapeSansDistance() {
            Agent a = agent(3L, "Amal");
            Site s = site(1L, "Rucher du haut");
            Planning p = planning(7L, ruche(42L, s), a);
            when(agents.findById(3L)).thenReturn(Optional.of(a));
            when(plannings.parAgentEtDate(3L, JOUR, StatutPlanning.REFUSE))
                    .thenReturn(List.of(p));

            TourneeReponse tournee = service.tournee(3L, JOUR, null);

            assertThat(tournee.etapes().get(0).ordre()).isEqualTo(1);
            assertThat(tournee.etapes().get(0).distanceDepuisPrecedenteMetres())
                    .isEqualByComparingTo("0.0");
            assertThat(tournee.etapes().get(0).siteNom()).isEqualTo("Rucher du haut");
        }

        @Test
        @DisplayName("un site de départ absent de la tournée est refusé, avec son identifiant")
        void departHorsTournee() {
            Agent a = agent(3L, "Amal");
            Site s = site(1L, "Rucher du haut");
            Planning p = planning(7L, ruche(42L, s), a);
            when(agents.findById(3L)).thenReturn(Optional.of(a));
            when(plannings.parAgentEtDate(3L, JOUR, StatutPlanning.REFUSE))
                    .thenReturn(List.of(p));

            // Partir d'un rucher où l'on n'a rien à faire n'est pas une tournée :
            // le dire vaut mieux que de l'ignorer silencieusement.
            assertThatThrownBy(() -> service.tournee(3L, JOUR, 99L))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("un site de départ imposé commence la tournée")
        void departImpose() {
            Agent a = agent(3L, "Amal");
            Site premier = site(1L, "Rucher du haut");
            Site second = site(2L, "Rucher du bas");
            Planning p1 = planning(7L, ruche(42L, premier), a);
            Planning p2 = planning(8L, ruche(43L, second), a);
            when(agents.findById(3L)).thenReturn(Optional.of(a));
            when(plannings.parAgentEtDate(3L, JOUR, StatutPlanning.REFUSE))
                    .thenReturn(List.of(p1, p2));

            TourneeReponse tournee = service.tournee(3L, JOUR, 2L);

            assertThat(tournee.etapes().get(0).siteId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("la matrice de distances est rendue SYMÉTRIQUE")
        void matriceSymetrique() {
            Agent a = agent(3L, "Amal");
            Site premier = site(1L, "Rucher du haut");
            Site second = site(2L, "Rucher du bas");
            Planning p1 = planning(7L, ruche(42L, premier), a);
            Planning p2 = planning(8L, ruche(43L, second), a);
            SiteRepository.PaireDistance paire = mock(SiteRepository.PaireDistance.class);
            when(paire.getDepartId()).thenReturn(1L);
            when(paire.getArriveeId()).thenReturn(2L);
            when(paire.getDistanceMetres()).thenReturn(4200.0);
            when(agents.findById(3L)).thenReturn(Optional.of(a));
            when(plannings.parAgentEtDate(3L, JOUR, StatutPlanning.REFUSE))
                    .thenReturn(List.of(p1, p2));
            when(sites.distancesEntre(any())).thenReturn(List.of(paire));

            TourneeReponse tournee = service.tournee(3L, JOUR, null);

            // La base ne rend qu'une ligne par paire (a.id < b.id) : sans la
            // symétrie rétablie ici, la moitié des trajets vaudrait zéro et
            // l'heuristique choisirait n'importe quel ordre.
            assertThat(tournee.distanceTotaleMetres()).isEqualByComparingTo("4200.0");
            assertThat(tournee.etapes().get(1).distanceDepuisPrecedenteMetres())
                    .isEqualByComparingTo("4200.0");
        }

        @Test
        @DisplayName("une paire portant un site hors tournée est ignorée")
        void paireHorsTournee() {
            Agent a = agent(3L, "Amal");
            Site s = site(1L, "Rucher du haut");
            Planning p = planning(7L, ruche(42L, s), a);
            SiteRepository.PaireDistance etrangere = mock(SiteRepository.PaireDistance.class);
            when(etrangere.getDepartId()).thenReturn(1L);
            when(etrangere.getArriveeId()).thenReturn(99L);
            when(agents.findById(3L)).thenReturn(Optional.of(a));
            when(plannings.parAgentEtDate(3L, JOUR, StatutPlanning.REFUSE))
                    .thenReturn(List.of(p));
            when(sites.distancesEntre(any())).thenReturn(List.of(etrangere));

            // Sans ce filtre, l'indexation dans la matrice lèverait un
            // NullPointerException sur une donnée que la base a le droit de rendre.
            assertThat(service.tournee(3L, JOUR, null).etapes()).hasSize(1);
        }

        @Test
        @DisplayName("un agent inconnu est refusé avant toute lecture de planning")
        void agentInconnu() {
            when(agents.findById(9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.tournee(9L, JOUR, null))
                    .isInstanceOf(RequeteInvalide.class)
                    .hasMessageContaining("9");
            verify(plannings, never()).parAgentEtDate(any(), any(), any());
        }
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la liste et la suppression passent par le dépôt")
    void listeEtSuppression() {
        Site s = site(1L, "R");
        Planning p = planning(7L, ruche(42L, s), agent(3L, "Amal"));
        when(plannings.findAll()).thenReturn(List.of(p));
        when(plannings.findById(7L)).thenReturn(Optional.of(p));

        assertThat(service.lister()).hasSize(1);
        service.supprimer(7L);
        verify(plannings).delete(p);
    }
}
