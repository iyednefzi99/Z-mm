package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Tache;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.service.regles.MoteurRegles;
import com.zumm.service.regles.RegleTache;
import com.zumm.service.regles.TacheProposee;
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
 * Tests du moteur de regles (SPRINT-22, lot A).
 *
 * <p>Ce qui est verifie ici n'est pas le contenu des regles — chacune a sa
 * logique — mais les trois proprietes du MOTEUR, celles dont l'absence ne se
 * verrait qu'apres plusieurs jours d'exploitation :
 *
 * <ol>
 *   <li>il n'engendre pas deux fois la meme tache. Sans cette garde, la liste se
 *       remplirait de doublons a chaque passage jusqu'a ce que personne ne
 *       l'ouvre plus ;
 *   <li>une tache engendree porte le code de sa regle, sans quoi elle ne peut
 *       pas se justifier a l'ecran ;
 *   <li>une regle qui ne propose rien n'est pas une erreur : c'est le cas normal
 *       d'une exploitation qui va bien.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class MoteurReglesTest {

    @Mock
    private TacheRepository taches;

    @Mock
    private RucheRepository ruches;

    @Mock
    private com.zumm.repository.MaterielRepository materiels;

    @Mock
    private NotificationAlerteService notifications;

    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    /** Une regle de laboratoire : elle propose ce qu'on lui demande de proposer. */
    private RegleTache regle(String code, TacheProposee... proposees) {
        RegleTache regle = mock(RegleTache.class);
        when(regle.proposer(JOUR)).thenReturn(List.of(proposees));
        // `code()` n'est lu que si la regle propose quelque chose : le stub est
        // donc laxiste, faute de quoi le cas « rien a proposer » echouerait sur
        // un stub inutilise.
        org.mockito.Mockito.lenient().when(regle.code()).thenReturn(code);
        return regle;
    }

    private TacheProposee proposee(String cle, String priorite) {
        return new TacheProposee(cle, "Retirer le traitement", 42L, JOUR.plusDays(3),
                priorite, "traitement");
    }

    private MoteurRegles moteur(RegleTache... regles) {
        return new MoteurRegles(List.of(regles), taches, ruches, materiels, notifications);
    }

    @BeforeEach
    void tacheEnregistreeTelleQuelle() {
        // Laxiste : deux tests verifient justement qu'AUCUNE tache n'est
        // enregistree, et un stub strict les ferait echouer pour la mauvaise raison.
        org.mockito.Mockito.lenient().when(taches.save(any(Tache.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient().when(ruches.findById(42L)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("engendre la tache proposee, avec le code de sa regle")
    void engendreEtJustifie() {
        when(taches.existsByCleDeclencheur(anyString())).thenReturn(false);

        List<Tache> creees = moteur(regle("carence-retrait",
                proposee("carence-retrait:7", "haute"))).executer(JOUR);

        assertThat(creees).hasSize(1);
        Tache tache = creees.get(0);
        // Sans le code, la tache apparait sans qu'on sache pourquoi — et une
        // tache qu'on ne s'explique pas est une tache qu'on ignore.
        assertThat(tache.getRegleCode()).isEqualTo("carence-retrait");
        assertThat(tache.getCleDeclencheur()).isEqualTo("carence-retrait:7");
        assertThat(tache.engendree()).isTrue();
        assertThat(tache.getPriorite()).isEqualTo("haute");
        assertThat(tache.getCategorie()).isEqualTo("traitement");
    }

    @Test
    @DisplayName("n'engendre pas deux fois la meme tache")
    void idempotent() {
        // Deuxieme passage de la journee : la cle existe deja.
        when(taches.existsByCleDeclencheur("carence-retrait:7")).thenReturn(true);

        List<Tache> creees = moteur(regle("carence-retrait",
                proposee("carence-retrait:7", "haute"))).executer(JOUR);

        assertThat(creees).isEmpty();
        verify(taches, never()).save(any());
    }

    @Test
    @DisplayName("une regle qui ne propose rien n'est pas une erreur")
    void aucuneProposition() {
        assertThat(moteur(regle("carence-retrait")).executer(JOUR)).isEmpty();
        verify(taches, never()).save(any());
    }

    @Test
    @DisplayName("notifie les taches critiques, et elles seules")
    void notifieLesCritiques() {
        when(taches.existsByCleDeclencheur(anyString())).thenReturn(false);

        moteur(regle("varroa-traiter", proposee("varroa-traiter:1", "critique")),
                regle("carence-retrait", proposee("carence-retrait:2", "haute")))
                .executer(JOUR);

        // Notifier chaque tache creee reviendrait a n'en notifier aucune : les
        // messages seraient filtres des la troisieme semaine, y compris ceux qui
        // comptent.
        verify(notifications).notifierTacheCritique(any(Tache.class));
    }

    @Test
    @DisplayName("execute toutes les regles, pas seulement la premiere")
    void executeToutesLesRegles() {
        when(taches.existsByCleDeclencheur(anyString())).thenReturn(false);

        List<Tache> creees = moteur(
                regle("carence-retrait", proposee("carence-retrait:1", "haute")),
                regle("controle-ponte", proposee("controle-ponte:2", "haute")),
                regle("reserves-basses", proposee("reserves-basses:3", "critique")))
                .executer(JOUR);

        assertThat(creees).hasSize(3);
        assertThat(creees).extracting(Tache::getCleDeclencheur)
                .containsExactly("carence-retrait:1", "controle-ponte:2", "reserves-basses:3");
    }
}
