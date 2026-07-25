package com.zumm.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.Alerte;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ruche;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.TypeIndicateur;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Tests unitaires de la notification d'alerte (US-041, SPRINT-09) : envoi effectif
 * quand tout est réuni, et silence tolérant sinon (désactivé, sans SMTP, sans e-mail).
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
}
