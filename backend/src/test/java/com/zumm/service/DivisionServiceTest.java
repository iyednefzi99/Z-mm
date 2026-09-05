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
import com.zumm.domain.Division;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.DivisionRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.DivisionCorps;
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
 * Divisions de colonies (SPRINT-21, lot 3 du plan de couverture).
 *
 * <p>70,8 % d'instructions et 60 % de branches. Deux refus y sont
 * <strong>traduits en 400 plutôt qu'en violation de contrainte</strong>, et
 * c'est tout leur intérêt : {@code uq_division_fille} garantit déjà l'unicité
 * en base, mais une erreur SQL en 500 n'apprend rien à l'apiculteur qui vient
 * de se tromper de ruche.
 *
 * <p>Et une troisième décision, la même que pour les captures d'essaim :
 * l'origine de la fille est <strong>alignée</strong> sur l'événement qui
 * l'explique ({@code division}) quand elle n'est pas déjà renseignée. La
 * filiation et l'origine disent la même chose ; les laisser diverger ferait
 * mentir la statistique par origine que le SPRINT-20 venait de rendre possible.
 */
@ExtendWith(MockitoExtension.class)
class DivisionServiceTest {

    @Mock private DivisionRepository divisions;
    @Mock private RucheRepository ruches;
    @Mock private AgentRepository agents;
    @Mock private VisiteRepository visites;

    private DivisionService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 4, 20);

    @BeforeEach
    void monter() {
        service = new DivisionService(divisions, ruches, agents, visites);
        lenient().when(divisions.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(divisions.findByFille_Id(any())).thenReturn(Optional.empty());
    }

    private static Agent agent() {
        Agent a = mock(Agent.class);
        lenient().when(a.getId()).thenReturn(3L);
        lenient().when(a.getNom()).thenReturn("Amal");
        return a;
    }

    private static Ruche ruche(Long id, String origine) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getOrigine()).thenReturn(origine);
        return r;
    }

    /** Une division simulee, munie de ce que {@code DivisionReponse.de} lit. */
    private static Division division(Ruche mere) {
        // Meme raison que pour les captures : l'agent d'abord.
        Agent a = agent();
        Division d = mock(Division.class);
        lenient().when(d.getMere()).thenReturn(mere);
        lenient().when(d.getAgent()).thenReturn(a);
        lenient().when(d.getDateDivision()).thenReturn(JOUR);
        return d;
    }

    private static DivisionCorps corps(Long mereId, Long filleId, Long visiteId) {
        return new DivisionCorps(mereId, filleId, 3L, visiteId, JOUR, "essaim_artificiel",
                3, 2, "cellule_royale", null);
    }

    // ── Les deux refus ──────────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche ne peut pas être sa propre fille")
    void filleEgaleMere() {
        Ruche mere = ruche(42L, null);
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));

        assertThatThrownBy(() -> service.enregistrer(corps(42L, 42L, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("sa propre fille");
        verify(divisions, never()).save(any());
    }

    @Test
    @DisplayName("une ruche n'a qu'une mère : la seconde division est refusée en 400")
    void filleDejaIssueDUneDivision() {
        Ruche mere = ruche(42L, null);
        Ruche fille = ruche(43L, null);
        Division existante = mock(Division.class);
        when(existante.getId()).thenReturn(7L);
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        when(ruches.findById(43L)).thenReturn(Optional.of(fille));
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));
        when(divisions.findByFille_Id(43L)).thenReturn(Optional.of(existante));

        // `uq_division_fille` le garantit aussi, mais en 500 : le message ici
        // nomme la division existante, et l'apiculteur sait quoi corriger.
        assertThatThrownBy(() -> service.enregistrer(corps(42L, 43L, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("43")
                .hasMessageContaining("7");
    }

    // ── L'origine alignée ───────────────────────────────────────────────────

    @Test
    @DisplayName("la fille reçoit l'origine « division » quand elle n'en a pas")
    void originePoseeQuandAbsente() {
        Ruche mere = ruche(42L, null);
        Ruche fille = ruche(43L, null);
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        when(ruches.findById(43L)).thenReturn(Optional.of(fille));
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));

        service.enregistrer(corps(42L, 43L, null));

        verify(fille).setOrigine("division");
    }

    @Test
    @DisplayName("une origine déjà renseignée n'est jamais écrasée")
    void origineDejaRenseignee() {
        Ruche mere = ruche(42L, null);
        Ruche fille = ruche(43L, "essaim_capture");
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        when(ruches.findById(43L)).thenReturn(Optional.of(fille));
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));

        service.enregistrer(corps(42L, 43L, null));

        verify(fille, never()).setOrigine(any());
    }

    // ── Rattachements ───────────────────────────────────────────────────────

    @Test
    @DisplayName("une division sans fille s'enregistre : la fille arrive plus tard")
    void divisionSansFille() {
        Ruche mere = ruche(42L, null);
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));

        service.enregistrer(corps(42L, null, null));

        verify(divisions).save(any(Division.class));
    }

    @Test
    @DisplayName("une ruche mère inconnue est refusée avec son identifiant")
    void mereInconnue() {
        when(ruches.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(corps(99L, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("un agent inconnu est refusé")
    void agentInconnu() {
        Ruche mere = ruche(42L, null);
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        when(agents.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(corps(42L, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("3");
    }

    @Test
    @DisplayName("une visite inconnue est refusée ; aucune visite est accepté")
    void visiteRattachee() {
        Ruche mere = ruche(42L, null);
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));
        when(visites.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enregistrer(corps(42L, null, 8L)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("8");

        // Sans visite : le rattachement est facultatif, on divise souvent sans
        // ouvrir de rapport.
        service.enregistrer(corps(42L, null, null));
        verify(divisions).save(any(Division.class));
    }

    @Test
    @DisplayName("une visite connue est rattachée")
    void visiteConnue() {
        Ruche mere = ruche(42L, null);
        Visite visite = mock(Visite.class);
        when(ruches.findById(42L)).thenReturn(Optional.of(mere));
        Agent lAgent = agent();
        when(agents.findById(3L)).thenReturn(Optional.of(lAgent));
        when(visites.findById(8L)).thenReturn(Optional.of(visite));

        service.enregistrer(corps(42L, null, 8L));

        verify(divisions).save(any(Division.class));
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le registre d'une ruche inconnue sort en 404, pas en liste vide")
    void registreSurRucheInconnue() {
        when(ruches.findById(999L)).thenReturn(Optional.empty());

        // Une liste vide serait indiscernable d'une ruche jamais divisée.
        assertThatThrownBy(() -> service.registre(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.filiation(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("la filiation rend les deux sens en une seule lecture")
    void filiationDansLesDeuxSens() {
        Ruche r = ruche(42L, null);
        Ruche mere = ruche(41L, null);
        Division division = division(mere);
        when(ruches.findById(42L)).thenReturn(Optional.of(r));
        when(divisions.filiation(42L)).thenReturn(List.of(division));

        // « D'où vient-elle » et « qu'a-t-elle donné » se posent au même moment,
        // sur le même écran, et se répondent avec la même table.
        assertThat(service.filiation(42L)).hasSize(1);
    }

    @Test
    @DisplayName("le registre d'une ruche connue passe par son finder")
    void registre() {
        Ruche r = ruche(42L, null);
        Ruche mere = ruche(42L, null);
        Division division = division(mere);
        when(ruches.findById(42L)).thenReturn(Optional.of(r));
        when(divisions.findByMere_IdOrderByDateDivisionDescIdDesc(42L))
                .thenReturn(List.of(division));

        assertThat(service.registre(42L)).hasSize(1);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt, et refuse une division inconnue")
    void suppression() {
        Division division = mock(Division.class);
        when(divisions.findById(1L)).thenReturn(Optional.of(division));
        when(divisions.findById(999L)).thenReturn(Optional.empty());

        service.supprimer(1L);

        verify(divisions).delete(division);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }
}
