package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.Alerte;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ruche;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Tache;
import com.zumm.domain.TypeIndicateur;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Tests unitaires de la notification d'alerte (US-041, SPRINT-09) : envoi effectif
 * quand tout est réuni, et silence tolérant sinon (désactivé, sans SMTP, sans e-mail).
 *
 * <p>Complété au lot 1 du plan de couverture, où la classe était à 59,8 %. Trois
 * choses n'étaient vérifiées nulle part, et chacune est une promesse écrite dans
 * la javadoc du service :
 *
 * <ol>
 *   <li><strong>Un destinataire par message.</strong> Le §10 du document d'écart
 *       rapporte que BeeKeepPal a envoyé des rappels avec plusieurs adresses en
 *       copie visible. « Structurellement impossible ici » n'était pas testé.</li>
 *   <li><strong>Un échec d'envoi n'est jamais fatal.</strong> La tâche et
 *       l'alerte sont déjà persistées ; une exception qui remonterait ferait
 *       échouer l'ingestion pour un courriel.</li>
 *   <li><strong>« Pas d'adresse » et « ne veut pas » sont distincts</strong>, et
 *       les deux font taire la notification. Les confondre ferait passer pour un
 *       oubli un réglage que l'agent a posé (SPRINT-25).</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class NotificationAlerteServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> expediteurs;

    @Mock
    private JavaMailSender mailSender;

    private Alerte alerteAvecEmail(String email) {
        Agent responsable = new Agent("Amine", RoleAgent.APICULTEUR, null);
        responsable.setEmail(email);
        Ruche ruche = new Ruche("Langstroth", null, null, EtatRuche.ACTIVE);
        ruche.setAgentResponsable(responsable);
        return new Alerte(ruche, TypeIndicateur.POIDS, Alerte.CRITIQUE, "Poids bas", BigDecimal.TEN);
    }

    @Test
    @DisplayName("envoie un e-mail à l'agent responsable quand activé et SMTP présent")
    void envoieQuandActive() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        service.notifierOuverture(alerteAvecEmail("amine@exemple.tn"));

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("ne fait rien quand les notifications sont désactivées")
    void silenceQuandDesactive() {
        var service = new NotificationAlerteService(expediteurs, false, "alertes@zumm.local");

        service.notifierOuverture(alerteAvecEmail("amine@exemple.tn"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("ne fait rien quand l'agent responsable n'a pas d'e-mail")
    void silenceSansEmail() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        service.notifierOuverture(alerteAvecEmail(null));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("ne fait rien quand l'agent a refusé les notifications")
    void silenceQuandAgentARefuse() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");
        Alerte alerte = alerteAvecEmail("amine@exemple.tn");
        alerte.getRuche().getAgentResponsable().setNotificationsEmail(false);

        service.notifierOuverture(alerte);

        // « Ne veut pas » est une décision, pas un défaut de paramétrage : le
        // réglage de l'agent l'emporte sur le réglage global.
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("notifications activées sans SMTP configuré : silence, pas d'erreur")
    void silenceSansExpediteur() {
        when(expediteurs.getIfAvailable()).thenReturn(null);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        assertThatCode(() -> service.notifierOuverture(alerteAvecEmail("amine@exemple.tn")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("un échec d'envoi ne remonte jamais : l'alerte est déjà persistée")
    void echecDEnvoiNonFatal() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("SMTP injoignable"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        assertThatCode(() -> service.notifierOuverture(alerteAvecEmail("amine@exemple.tn")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("le message d'alerte ne porte qu'UNE adresse, et nomme la ruche")
    void unSeulDestinataireParMessage() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        service.notifierOuverture(alerteAvecEmail("amine@exemple.tn"));

        ArgumentCaptor<SimpleMailMessage> capture =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(capture.capture());
        SimpleMailMessage message = capture.getValue();
        // La faute de BeeKeepPal, rendue impossible : une seule adresse, et
        // aucune copie. Ce test tombe si quelqu'un passe un jour à `setTo(String[])`.
        assertThat(message.getTo()).containsExactly("amine@exemple.tn");
        assertThat(message.getCc()).isNull();
        assertThat(message.getBcc()).isNull();
        assertThat(message.getFrom()).isEqualTo("alertes@zumm.local");
        assertThat(message.getSubject()).contains(Alerte.CRITIQUE);
        assertThat(message.getText()).contains("Poids bas");
    }

    // ── Tâche critique (SPRINT-22, lot A) ───────────────────────────────────

    private Tache tacheCritique(String email, boolean engendree) {
        Agent agent = new Agent("Amine", RoleAgent.APICULTEUR, null);
        agent.setEmail(email);
        Tache tache = new Tache("Retirer les hausses");
        tache.setAgent(agent);
        tache.setPriorite("critique");
        tache.setEcheance(LocalDate.of(2026, 9, 12));
        if (engendree) {
            tache.engendreePar("carence-retrait", "carence-retrait:42");
        }
        return tache;
    }

    @Test
    @DisplayName("une tâche engendrée cite la règle qui l'a proposée")
    void tacheEngendreeCiteSaRegle() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        service.notifierTacheCritique(tacheCritique("amine@exemple.tn", true));

        ArgumentCaptor<SimpleMailMessage> capture =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(capture.capture());
        SimpleMailMessage message = capture.getValue();
        assertThat(message.getTo()).containsExactly("amine@exemple.tn");
        assertThat(message.getSubject()).contains("Retirer les hausses");
        // Une tâche qu'on n'a pas saisie soi-même doit dire d'où elle vient,
        // sans quoi elle ressemble à une tâche apparue toute seule.
        assertThat(message.getText()).contains("carence-retrait").contains("2026-09-12");
    }

    @Test
    @DisplayName("une tâche saisie à la main le dit, et une échéance absente aussi")
    void tacheSaisieEtSansEcheance() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");
        Tache tache = tacheCritique("amine@exemple.tn", false);
        tache.setEcheance(null);

        service.notifierTacheCritique(tache);

        ArgumentCaptor<SimpleMailMessage> capture =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(capture.capture());
        assertThat(capture.getValue().getText())
                .contains("Saisie manuellement")
                .contains("non datee");
    }

    @Test
    @DisplayName("tâche critique : silence si désactivé, sans agent, ou agent injoignable")
    void tacheCritiqueSilencieuse() {
        var desactive = new NotificationAlerteService(expediteurs, false, "alertes@zumm.local");
        desactive.notifierTacheCritique(tacheCritique("amine@exemple.tn", true));

        var actif = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");
        actif.notifierTacheCritique(new Tache("Sans agent"));
        actif.notifierTacheCritique(tacheCritique(null, true));
        actif.notifierTacheCritique(tacheCritique("   ", true));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("tâche critique : un échec d'envoi ne fait pas échouer la création")
    void tacheCritiqueEchecNonFatal() {
        when(expediteurs.getIfAvailable()).thenReturn(mailSender);
        doThrow(new MailSendException("SMTP injoignable"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        // C'est la tâche qui compte ; le courriel n'est qu'un rappel.
        assertThatCode(() -> service.notifierTacheCritique(tacheCritique("a@exemple.tn", true)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tâche critique : silence quand aucun SMTP n'est configuré")
    void tacheCritiqueSansExpediteur() {
        when(expediteurs.getIfAvailable()).thenReturn(null);
        var service = new NotificationAlerteService(expediteurs, true, "alertes@zumm.local");

        assertThatCode(() -> service.notifierTacheCritique(tacheCritique("a@exemple.tn", true)))
                .doesNotThrowAnyException();
    }
}
