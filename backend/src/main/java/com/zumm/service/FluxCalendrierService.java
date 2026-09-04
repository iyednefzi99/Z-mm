package com.zumm.service;

import com.zumm.domain.AbonnementCalendrier;
import com.zumm.tenant.PorteeContext;
import com.zumm.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sert le calendrier d'un abonne, hors de toute session (SPRINT-21).
 *
 * <p><strong>Pourquoi une classe pour trois lignes.</strong> Cette methode fait
 * quelque chose qu'aucune autre du depot ne fait : elle POSE elle-meme le
 * contexte de tenant et de portee, au lieu de le recevoir de la chaine de
 * filtres. C'est inevitable — le client de calendrier n'a pas de session, et
 * c'est justement le jeton qui designe l'exploitation — mais cela merite d'etre
 * isole dans un seul endroit, nomme, commente et testable, plutot que dilue dans
 * un controleur.
 *
 * <p><strong>Et pourquoi {@code NOT_SUPPORTED}.</strong> Le fournisseur de
 * connexions lit les deux contextes au moment ou il OUVRE une connexion. Si
 * cette methode etait transactionnelle, la connexion serait prise avant que les
 * contextes soient poses, et la requete du calendrier partirait sans tenant — en
 * rendant zero visite, silencieusement. En restant hors transaction, elle laisse
 * {@code AgendaIcsService} ouvrir la sienne apres coup, contextes en place.
 *
 * <p>Le nettoyage est ici et non dans {@code TenantFilter} : le chemin du flux
 * est exempte de ce filtre (il doit repondre sans jeton), donc son {@code finally}
 * ne s'executerait pas. Un {@code ThreadLocal} laisse en place fuiterait sur la
 * requete suivante servie par le meme fil — et fuiterait ici une EXPLOITATION.
 */
@Service
public class FluxCalendrierService {

    /** Fenetre servie a un client de calendrier : le passe proche et la saison a venir. */
    private static final int JOURS_AVANT = 30;
    private static final int JOURS_APRES = 120;

    private final AbonnementCalendrierService abonnements;
    private final AgendaIcsService agenda;

    public FluxCalendrierService(AbonnementCalendrierService abonnements, AgendaIcsService agenda) {
        this.abonnements = abonnements;
        this.agenda = agenda;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<String> calendrier(String jeton) {
        Optional<AbonnementCalendrier> trouve = abonnements.resoudre(jeton);
        if (trouve.isEmpty()) {
            return Optional.empty();
        }
        AbonnementCalendrier abonnement = trouve.get();
        TenantContext.definir(abonnement.getTenantId());
        // La portee de l'agent, et pas la portee globale : un abonnement publie
        // l'agenda d'UNE personne. Un jeton qui rendrait tout le parc serait une
        // clef d'exploitation deguisee en fichier de calendrier.
        PorteeContext.definir(PorteeContext.Portee.agent(abonnement.getAgentId()));
        try {
            LocalDate aujourdhui = LocalDate.now();
            return Optional.of(agenda.calendrierAgent(
                    abonnement.getAgentId(),
                    aujourdhui.minusDays(JOURS_AVANT),
                    aujourdhui.plusDays(JOURS_APRES)));
        } finally {
            PorteeContext.effacer();
            TenantContext.effacer();
        }
    }
}
