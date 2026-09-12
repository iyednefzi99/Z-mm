package com.zumm.securite;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.Agent;
import com.zumm.domain.AuditEntree;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Tache;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.AuditEntreeRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.service.NotificationAlerteService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detecte l'accumulation de refus RBAC d'un meme acteur, et alerte (SPRINT-34).
 *
 * <p>{@code REVUE-CONSOLIDEE.md} §5 le pointait depuis le SPRINT-09 : « le
 * journal d'audit enregistre, personne ne le lit en continu ». Ce detecteur
 * ferme ce point pour le seul signal qui s'y prete sans base comportementale —
 * des refus RBAC repetes, qui disent « role mal attribue » ou « quelqu'un
 * teste des routes hors de son role » sans qu'aucune moyenne historique ne
 * soit necessaire pour le juger anormal.
 *
 * <p><strong>Pourquoi pas {@code MoteurRegles}/{@code RegleTache}.</strong> Ce
 * moteur est deliberement NON planifie ({@code RegleController}) : il attend
 * une commande explicite, ce qui convient a des regles metier qu'un
 * responsable declenche a son rythme (reserves basses, stock). Une anomalie
 * d'acces est l'inverse par nature — un evenement qui doit reagir au moment ou
 * il se produit, pas a la prochaine execution manuelle. Ce detecteur est donc
 * appele directement depuis le point ou le refus se produit
 * ({@code SecurityConfig}), et non depuis le moteur de regles — mais il
 * REUTILISE son vocabulaire : une tache {@code origine=regle}, une
 * {@code cle_declencheur} pour l'anti-doublon, une notification reservee aux
 * taches critiques.
 *
 * <p><strong>La cle de deduplication est horaire</strong>
 * ({@code anomalie-acces:<acteur>:<heure tronquee>}) : une fois le seuil
 * franchi, les refus suivants du meme acteur ne recreent pas la tache dans la
 * meme heure. Sans cette borne, un acteur qui continue a essuyer des refus
 * remplirait la liste d'un doublon a chaque appel.
 */
@Component
public class DetecteurAnomalieAcces {

    private static final Logger LOG = LoggerFactory.getLogger(DetecteurAnomalieAcces.class);

    /** Code inscrit sur la tache engendree — {@link Tache#engendreePar}. */
    private static final String CODE = "anomalie-acces";

    private final AuditEntreeRepository audits;
    private final TacheRepository taches;
    private final AgentRepository agents;
    private final ConfigurationMetier config;
    private final NotificationAlerteService notifications;

    public DetecteurAnomalieAcces(AuditEntreeRepository audits, TacheRepository taches,
            AgentRepository agents, ConfigurationMetier config, NotificationAlerteService notifications) {
        this.audits = audits;
        this.taches = taches;
        this.agents = agents;
        this.config = config;
        this.notifications = notifications;
    }

    /**
     * Journalise un refus RBAC et, au-dela du seuil configure sur la fenetre
     * glissante, engendre une tache critique et alerte les responsables et
     * l'administrateur.
     *
     * @param acteur nom lisible de l'appelant refuse — un 403 suppose une
     *               authentification reussie, {@code acteur} n'est donc jamais
     *               {@code null} ni {@value IdentiteAppelant#ACTEUR_SYSTEME}
     * @param resume ce qui a ete refuse (methode et chemin), pour le journal
     */
    @Transactional
    public void surRefus(String acteur, String resume) {
        audits.save(new AuditEntree(acteur, AuditEntree.REFUS, "Acces", null, resume));

        int seuil = config.seuils().seuilRefusAnomalie();
        int fenetreMinutes = config.seuils().fenetreRefusAnomalieMinutes();
        Instant depuis = Instant.now().minusSeconds(fenetreMinutes * 60L);
        long nombreRefus = audits.countByActeurAndActionAndInstantAfter(acteur, AuditEntree.REFUS, depuis);
        if (nombreRefus < seuil) {
            return;
        }

        String cle = CODE + ":" + acteur + ":" + Instant.now().truncatedTo(ChronoUnit.HOURS);
        if (taches.existsByCleDeclencheur(cle)) {
            return;
        }

        Tache tache = new Tache(
                "Anomalie d'acces : %d refus pour %s en %d min".formatted(nombreRefus, acteur, fenetreMinutes));
        tache.setPriorite("critique");
        tache.setCategorie("administratif");
        tache.setEcheance(LocalDate.now());
        tache.engendreePar(CODE, cle);
        taches.save(tache);

        List<Agent> destinataires = agents.findByRoleIn(List.of(RoleAgent.RESPONSABLE, RoleAgent.ADMIN));
        notifications.notifierAnomalieAcces(acteur, (int) nombreRefus, fenetreMinutes, destinataires);
        LOG.warn("Anomalie d'acces : {} refus pour {} en {} min - tache critique engendree.",
                nombreRefus, acteur, fenetreMinutes);
    }
}
