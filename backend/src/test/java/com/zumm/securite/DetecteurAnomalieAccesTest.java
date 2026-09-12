package com.zumm.securite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.domain.Agent;
import com.zumm.domain.AuditEntree;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Tache;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.AuditEntreeRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.service.NotificationAlerteService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests du detecteur d'anomalie d'acces (SPRINT-34).
 *
 * <p>Le seuil et la fenetre viennent de {@link SeuilsMetier#defauts()} : 5 refus
 * en 15 minutes. Ce qui est verifie ici, ce sont les trois proprietes dont
 * l'absence ne se verrait qu'apres coup :
 *
 * <ol>
 *   <li>chaque refus est journalise, seuil atteint ou non ;
 *   <li>la tache et la notification n'apparaissent qu'au seuil, pas avant ;
 *   <li>une fois la tache engendree, les refus suivants dans la meme heure ne
 *       la recreent pas — l'anti-doublon vit sur la cle, pas sur le compteur.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class DetecteurAnomalieAccesTest {

    @Mock
    private AuditEntreeRepository audits;

    @Mock
    private TacheRepository taches;

    @Mock
    private AgentRepository agents;

    @Mock
    private ConfigurationMetier configurationMetier;

    @Mock
    private NotificationAlerteService notifications;

    private DetecteurAnomalieAcces detecteur;

    @BeforeEach
    void avantChaqueTest() {
        when(configurationMetier.seuils()).thenReturn(SeuilsMetier.defauts());
        detecteur = new DetecteurAnomalieAcces(audits, taches, agents, configurationMetier, notifications);
    }

    @Test
    @DisplayName("journalise le refus, seuil atteint ou non")
    void journaliseToujoursLeRefus() {
        when(audits.countByActeurAndActionAndInstantAfter(eq("lea"), eq(AuditEntree.REFUS), any(Instant.class)))
                .thenReturn(1L);

        detecteur.surRefus("lea", "GET /api/depenses");

        ArgumentCaptor<AuditEntree> capture = ArgumentCaptor.forClass(AuditEntree.class);
        verify(audits).save(capture.capture());
        assertThat(capture.getValue().getActeur()).isEqualTo("lea");
        assertThat(capture.getValue().getAction()).isEqualTo(AuditEntree.REFUS);
        assertThat(capture.getValue().getResume()).isEqualTo("GET /api/depenses");
    }

    @Test
    @DisplayName("ne cree aucune tache sous le seuil")
    void aucuneTacheSousLeSeuil() {
        when(audits.countByActeurAndActionAndInstantAfter(anyString(), eq(AuditEntree.REFUS), any(Instant.class)))
                .thenReturn(4L);

        detecteur.surRefus("lea", "GET /api/depenses");

        verify(taches, never()).save(any(Tache.class));
        verify(notifications, never()).notifierAnomalieAcces(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("engendre une tache critique et alerte au seuil")
    void tacheCritiqueEtAlerteAuSeuil() {
        when(audits.countByActeurAndActionAndInstantAfter(eq("lea"), eq(AuditEntree.REFUS), any(Instant.class)))
                .thenReturn(5L);
        when(taches.existsByCleDeclencheur(anyString())).thenReturn(false);
        when(taches.save(any(Tache.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Agent responsable = mock(Agent.class);
        when(agents.findByRoleIn(List.of(RoleAgent.RESPONSABLE, RoleAgent.ADMIN)))
                .thenReturn(List.of(responsable));

        detecteur.surRefus("lea", "GET /api/depenses");

        ArgumentCaptor<Tache> capture = ArgumentCaptor.forClass(Tache.class);
        verify(taches).save(capture.capture());
        Tache tache = capture.getValue();
        assertThat(tache.getPriorite()).isEqualTo("critique");
        assertThat(tache.getCategorie()).isEqualTo("administratif");
        assertThat(tache.getOrigine()).isEqualTo("regle");
        assertThat(tache.getCleDeclencheur()).startsWith("anomalie-acces:lea:");

        verify(notifications).notifierAnomalieAcces("lea", 5, 15, List.of(responsable));
    }

    @Test
    @DisplayName("ne recree rien dans la meme heure : la cle existe deja")
    void aucunDoublonDansLaMemeHeure() {
        when(audits.countByActeurAndActionAndInstantAfter(eq("lea"), eq(AuditEntree.REFUS), any(Instant.class)))
                .thenReturn(6L);
        when(taches.existsByCleDeclencheur(anyString())).thenReturn(true);

        detecteur.surRefus("lea", "GET /api/depenses");

        verify(taches, never()).save(any(Tache.class));
        verify(notifications, never()).notifierAnomalieAcces(anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }
}
