package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Alerte;
import com.zumm.domain.Ruche;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Notification e-mail à l'ouverture d'une alerte de seuil (US-041, SPRINT-09).
 *
 * <p>Quand une alerte s'ouvre sur une ruche, l'agent qui en est responsable est
 * prévenu par e-mail, s'il dispose d'une adresse. Le service est <b>tolérant aux
 * pannes</b> : un envoi impossible (SMTP indisponible, adresse manquante,
 * notifications désactivées) est journalisé mais n'interrompt JAMAIS l'ingestion
 * de la mesure qui a déclenché l'alerte.
 *
 * <p>L'émission est conditionnée par {@code zumm.notifications.email.enabled} et par
 * la présence d'un {@link JavaMailSender} (autoconfiguré dès que {@code spring.mail.host}
 * est défini). En développement et en test, sans SMTP, le service se contente de
 * journaliser — d'où l'injection optionnelle via {@link ObjectProvider}.
 */
@Service
public class NotificationAlerteService {

    private static final Logger log = LoggerFactory.getLogger(NotificationAlerteService.class);

    private final ObjectProvider<JavaMailSender> expediteurs;
    private final boolean active;
    private final String expediteur;

    public NotificationAlerteService(
            ObjectProvider<JavaMailSender> expediteurs,
            @Value("${zumm.notifications.email.enabled:false}") boolean active,
            @Value("${zumm.notifications.email.expediteur:alertes@zumm.local}") String expediteur) {
        this.expediteurs = expediteurs;
        this.active = active;
        this.expediteur = expediteur;
    }

    /**
     * Notifie l'ouverture d'une alerte. Sans effet (hormis un journal) si les
     * notifications sont désactivées, si aucun serveur SMTP n'est configuré, ou si
     * l'agent responsable n'a pas d'adresse e-mail.
     */
    public void notifierOuverture(Alerte alerte) {
        if (!active) {
            return;
        }
        JavaMailSender expediteurMail = expediteurs.getIfAvailable();
        if (expediteurMail == null) {
            log.warn("Notifications e-mail activées mais aucun JavaMailSender configuré (spring.mail.host).");
            return;
        }
        String destinataire = destinataire(alerte.getRuche());
        if (destinataire == null) {
            log.info("Alerte {} sur ruche {} : aucun agent responsable avec e-mail, pas de notification.",
                    alerte.getNiveau(), rucheId(alerte));
            return;
        }
        try {
            expediteurMail.send(composer(alerte, destinataire));
            log.info("Notification d'alerte envoyée à {} (ruche {}).", destinataire, rucheId(alerte));
        } catch (MailException e) {
            // Jamais fatal : la mesure a déjà été ingérée et l'alerte persistée.
            log.warn("Échec de l'envoi de la notification d'alerte à {} : {}", destinataire, e.getMessage());
        }
    }

    private SimpleMailMessage composer(Alerte alerte, String destinataire) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(expediteur);
        message.setTo(destinataire);
        message.setSubject("[Zümm] Alerte %s — ruche %s".formatted(alerte.getNiveau(), rucheId(alerte)));
        message.setText(
                "Une alerte vient de s'ouvrir sur l'une de vos ruches.\n\n"
                        + "Ruche      : " + rucheId(alerte) + "\n"
                        + "Indicateur : " + alerte.getTypeIndicateur() + "\n"
                        + "Niveau     : " + alerte.getNiveau() + "\n"
                        + "Détail     : " + alerte.getMessage() + "\n\n"
                        + "Connectez-vous à la console Zümm pour inspecter la colonie.\n");
        return message;
    }

    /** Adresse de l'agent responsable de la ruche, ou {@code null} s'il n'en a pas. */
    private String destinataire(Ruche ruche) {
        Agent responsable = ruche == null ? null : ruche.getAgentResponsable();
        if (responsable == null) {
            return null;
        }
        String email = responsable.getEmail();
        return (email == null || email.isBlank()) ? null : email;
    }

    private Long rucheId(Alerte alerte) {
        return alerte.getRuche() == null ? null : alerte.getRuche().getId();
    }
}
