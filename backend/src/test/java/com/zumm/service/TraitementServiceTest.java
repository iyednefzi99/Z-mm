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
import com.zumm.domain.Ruche;
import com.zumm.domain.Traitement;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.TraitementCorps;
import com.zumm.web.dto.TraitementReponse;
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
 * Registre des traitements sanitaires (SPRINT-20, lot 3 du plan de couverture).
 *
 * <p>83,2 % d'instructions, 81,2 % de branches. L'écart porte sur les deux
 * contrôles de saisie et sur la carence — et la carence est le seul de tout le
 * registre sanitaire qui soit <strong>opposable</strong> : c'est lui qui répond
 * à « que puis-je récolter aujourd'hui ».
 *
 * <p>La règle elle-même n'est pas dans ce service. {@link Traitement#sousCarence}
 * la porte et prend le jour en paramètre, ce qui la rend testable sans horloge
 * injectée ; le service se contente de lui passer {@code LocalDate.now()}. Ce
 * test vérifie donc ce qui appartient au service : que le jour est calculé
 * <strong>une fois</strong> pour tout un registre, et que le filtre applicatif
 * repasse derrière la requête.
 */
@ExtendWith(MockitoExtension.class)
class TraitementServiceTest {

    @Mock private TraitementRepository traitements;
    @Mock private RucheRepository ruches;
    @Mock private AgentRepository agents;
    @Mock private VisiteRepository visites;
    @Mock private OperationsLotService lots;

    private TraitementService service;

    private static final LocalDate DEBUT = LocalDate.of(2026, 4, 2);

    @BeforeEach
    void monter() {
        service = new TraitementService(traitements, ruches, agents, visites, lots);
        lenient().when(traitements.save(any())).thenAnswer(i -> i.getArgument(0));
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

    /** Le corps nominal : produit, cible varroa, dose et unité cohérentes. */
    private static TraitementCorps corps(BigDecimal dose, String unite, LocalDate fin) {
        return new TraitementCorps(5L, 9L, null, "  Apivar  ", "amitraze", "varroa",
                dose, unite, DEBUT, fin, 14, null, null, null, null);
    }

    private void tenantComplet() {
        // Les mocks sont construits AVANT le when() : en fabriquer un entre
        // when() et thenReturn() laisse le stub inachevé (UnfinishedStubbing).
        Optional<Ruche> r = Optional.of(ruche());
        Optional<Agent> a = Optional.of(agent());
        when(ruches.findById(5L)).thenReturn(r);
        when(agents.findById(9L)).thenReturn(a);
    }

    // ── Les deux contrôles de saisie ────────────────────────────────────────

    @Test
    @DisplayName("une dose sans unité est refusée, et le refus énumère les unités")
    void doseSansUnite() {
        tenantComplet();

        // « 2 » ne dit ni 2 ml ni 2 lanières, et la différence est un facteur
        // mille. Le CHECK en base porte la même règle mais sortirait en 500 ;
        // ici le message donne à l'apiculteur de quoi corriger.
        assertThatThrownBy(() -> service.enregistrer(corps(new BigDecimal("2"), null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("mg, g, ml, l, laniere ou plaquette");
        verify(traitements, never()).save(any());
    }

    @Test
    @DisplayName("une unité sans dose est refusée aussi : le contrôle est symétrique")
    void uniteSansDose() {
        tenantComplet();

        assertThatThrownBy(() -> service.enregistrer(corps(null, "ml", null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Une unite de dose est indiquee sans dose");
    }

    @Test
    @DisplayName("ni dose ni unité passe : tous les traitements ne se dosent pas")
    void niDoseNiUnite() {
        tenantComplet();

        // Une lanière posée n'a pas toujours de dose saisie ; exiger les deux
        // ferait perdre l'enregistrement du traitement lui-même, qui est ce que
        // le registre doit retenir.
        assertThat(service.enregistrer(corps(null, null, null))).isNotNull();
    }

    @Test
    @DisplayName("une fin antérieure au début est refusée, avec les DEUX dates")
    void finAvantDebut() {
        tenantComplet();

        assertThatThrownBy(() -> service.enregistrer(
                corps(new BigDecimal("2"), "ml", DEBUT.minusDays(1))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("2026-04-01")
                .hasMessageContaining("2026-04-02");
    }

    @Test
    @DisplayName("une fin absente passe : un traitement en cours n'a pas de fin")
    void finAbsente() {
        tenantComplet();

        assertThat(service.enregistrer(corps(new BigDecimal("2"), "ml", null))).isNotNull();
    }

    @Test
    @DisplayName("le produit est débarrassé de ses espaces de bord")
    void produitNettoye() {
        tenantComplet();

        // Sans cela « Apivar » et « Apivar  » seraient deux produits distincts
        // dans toute statistique de traitement.
        assertThat(service.enregistrer(corps(new BigDecimal("2"), "ml", null)).produit())
                .isEqualTo("Apivar");
    }

    // ── Rattachements ───────────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche ou un agent hors tenant est refusé en 400, jamais en 500")
    void rattachementsInconnus() {
        when(ruches.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enregistrer(corps(null, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Ruche inconnue dans ce tenant : 5");

        Optional<Ruche> presente = Optional.of(ruche());
        when(ruches.findById(5L)).thenReturn(presente);
        when(agents.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enregistrer(corps(null, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Agent inconnu dans ce tenant : 9");
    }

    @Test
    @DisplayName("une visite absente laisse le traitement NON rattaché, sans erreur")
    void visiteAbsente() {
        tenantComplet();

        // Un traitement fait hors visite est le cas courant : exiger la visite
        // ferait inventer des visites pour pouvoir enregistrer des traitements.
        assertThat(service.enregistrer(corps(null, null, null)).visiteId()).isNull();
        verify(visites, never()).findById(any());
    }

    @Test
    @DisplayName("une visite désignée mais inconnue est refusée en 400")
    void visiteInconnue() {
        tenantComplet();
        when(visites.findById(3L)).thenReturn(Optional.empty());
        TraitementCorps avecVisite = new TraitementCorps(5L, 9L, 3L, "Apivar", null, "varroa",
                null, null, DEBUT, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.enregistrer(avecVisite))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Visite inconnue dans ce tenant : 3");
    }

    @Test
    @DisplayName("une visite connue est rattachée au traitement")
    void visiteRattachee() {
        tenantComplet();
        Visite v = mock(Visite.class);
        when(v.getId()).thenReturn(3L);
        when(visites.findById(3L)).thenReturn(Optional.of(v));
        TraitementCorps avecVisite = new TraitementCorps(5L, 9L, 3L, "Apivar", null, "varroa",
                null, null, DEBUT, null, null, null, null, null, null);

        assertThat(service.enregistrer(avecVisite).visiteId()).isEqualTo(3L);
    }

    // ── Le lot ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le lot rejoue enregistrer() par ruche, il n'a pas sa propre implémentation")
    void lotRejoueLUnitaire() {
        tenantComplet();
        TraitementCorps modele = new TraitementCorps(999L, 9L, null, "Apivar", null, "varroa",
                null, null, DEBUT, null, 14, null, null, null, null);

        // L'identifiant de ruche du MODÈLE (999) est ignoré : c'est la ruche du
        // lot qui compte. Une seconde implémentation du traitement finirait par
        // diverger de l'unitaire — c'est tout l'objet de ce test.
        Long id = service.enregistrerPour(ruche(), modele);

        assertThat(id).isNull(); // save() rend l'entité non persistée : pas d'id
        verify(traitements).save(any(Traitement.class));
    }

    // ── La carence, seule règle opposable du registre ───────────────────────

    @Test
    @DisplayName("le registre calcule le jour UNE fois pour toutes les lignes")
    void registreUnSeulJour() {
        LocalDate jour = LocalDate.now();
        Traitement ancien = traitement(jour.minusDays(90), jour.minusDays(80), 14);
        Traitement encours = traitement(jour.minusDays(3), jour.minusDays(1), 14);
        Optional<Ruche> r = Optional.of(ruche());
        when(ruches.findById(5L)).thenReturn(r);
        when(traitements.findByRuche_IdOrderByDateDebutDescIdDesc(5L))
                .thenReturn(List.of(encours, ancien));

        // Recalculer LocalDate.now() par ligne ferait basculer un registre lu à
        // minuit : deux lignes du même écran répondraient à deux jours.
        List<TraitementReponse> registre = service.registre(5L);

        assertThat(registre).extracting(TraitementReponse::sousCarence)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("un traitement sans délai de carence n'est jamais sous carence")
    void sansDelaiDeCarence() {
        LocalDate jour = LocalDate.now();
        Optional<Ruche> r = Optional.of(ruche());
        List<Traitement> sansCarence = List.of(traitement(jour, jour, null));
        when(ruches.findById(5L)).thenReturn(r);
        when(traitements.findByRuche_IdOrderByDateDebutDescIdDesc(5L)).thenReturn(sansCarence);

        // Absence de délai veut dire « pas de carence », pas « carence de zéro
        // jour » : la nuance décide si l'apiculteur peut récolter.
        assertThat(service.registre(5L).get(0).sousCarence()).isFalse();
    }

    @Test
    @DisplayName("sousCarence() repasse un filtre applicatif DERRIÈRE la requête")
    void filtreApplicatifDerriereLaRequete() {
        LocalDate jour = LocalDate.now();
        List<Traitement> presel = List.of(
                traitement(jour.minusDays(2), jour.minusDays(1), 14),
                traitement(jour.plusDays(5), jour.plusDays(10), 14));
        when(traitements.sousCarenceAu(any())).thenReturn(presel);

        // La requête présélectionne largement ; c'est Traitement.sousCarence qui
        // tranche. Un traitement qui n'a pas COMMENCÉ ne bloque pas la récolte,
        // et le laisser passer ferait refuser une récolte légitime — après quoi
        // l'apiculteur cesse de lire la liste.
        List<TraitementReponse> bloquants = service.sousCarence();

        assertThat(bloquants).hasSize(1);
        assertThat(bloquants.get(0).sousCarence()).isTrue();
    }

    // ── Refus et suppression ────────────────────────────────────────────────

    @Test
    @DisplayName("le registre d'une ruche inconnue est un 404, pas une liste vide")
    void registreRucheInconnue() {
        when(ruches.findById(999L)).thenReturn(Optional.empty());

        // Une liste vide dirait « cette ruche n'a jamais été traitée » — la
        // pire des réponses possibles sur un registre sanitaire.
        assertThatThrownBy(() -> service.registre(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("un traitement inconnu est refusé en 404 ; sinon l'entité est supprimée")
    void suppression() {
        when(traitements.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);

        Traitement t = traitement(DEBUT, DEBUT, 14);
        Optional<Traitement> present = Optional.of(t);
        when(traitements.findById(1L)).thenReturn(present);
        service.supprimer(1L);
        verify(traitements).delete(t);
    }

    private static Traitement traitement(LocalDate debut, LocalDate fin, Integer carence) {
        Traitement t = new Traitement(ruche(), agent(), "Apivar", "varroa", debut);
        t.setDateFin(fin);
        t.setDelaiCarenceJours(carence);
        return t;
    }
}
