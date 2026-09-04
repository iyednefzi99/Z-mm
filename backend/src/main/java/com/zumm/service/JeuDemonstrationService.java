package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Compartiment;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ferme;
import com.zumm.domain.Fermier;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.TraceDemonstration;
import com.zumm.domain.TypeCompartiment;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.FermierRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.TraceDemonstrationRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RegleMetierViolee;
import com.zumm.web.dto.EtatDemonstration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jeu de démonstration chargeable et <strong>retirable</strong> depuis
 * l'application (SPRINT-25, lot J).
 *
 * <p>Ferme le contournement du §13 : BeeLog Digital conseille à ses utilisateurs
 * de « configurer une seule ruche <em>test</em> avant de basculer
 * l'exploitation ». Le dépôt avait bien {@code infra/seed-demo.sql}, mais côté
 * exploitant, avec {@code psql}, sur le tenant de développement — rien qu'un
 * utilisateur puisse charger, et surtout retirer, depuis la console.
 *
 * <p><strong>Le mot qui compte est « réversible ».</strong> Une démonstration
 * qu'on ne peut pas défaire n'est pas une démonstration, c'est une pollution :
 * l'exploitation garderait pour toujours des ruches fictives mêlées aux vraies,
 * et plus personne n'oserait supprimer quoi que ce soit de peur de se tromper de
 * cible. D'où {@link TraceDemonstration} : la purge ne supprime que ce que le
 * chargement a créé, ligne par ligne — jamais une donnée saisie entre-temps,
 * même si elle porte le même nom.
 *
 * <p><strong>Trois verrous, et aucun n'est de trop.</strong>
 *
 * <ol>
 *   <li>la fonction est <strong>éteinte par défaut</strong>
 *       ({@code zumm.demonstration.activee}) : une production ne doit pas
 *       seulement refuser la démonstration, elle ne doit pas l'exposer ;
 *   <li>elle est réservée au rôle <strong>admin</strong> — écrire vingt lignes
 *       dans l'exploitation d'autrui n'est pas un geste d'apiculteur ;
 *   <li>un second chargement est <strong>refusé</strong> tant que le premier
 *       n'est pas purgé. Empiler deux jeux rendrait la trace ambiguë, et c'est
 *       la trace qui rend la purge sûre.
 * </ol>
 */
@Service
@Transactional
public class JeuDemonstrationService {

    private static final Logger log = LoggerFactory.getLogger(JeuDemonstrationService.class);

    /**
     * Marque portée par tout ce que le jeu crée.
     *
     * <p>Elle sert à l'utilisateur, pas au code : la purge s'appuie sur la
     * trace, jamais sur ce préfixe. Se fier au nom aurait détruit le rucher d'un
     * apiculteur ayant eu le tort d'appeler le sien « Démonstration ».
     */
    private static final String MARQUE = "[démo] ";

    private final TraceDemonstrationRepository traces;
    private final FermierRepository fermiers;
    private final FermeRepository fermes;
    private final SiteRepository sites;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final VisiteRepository visites;
    private final boolean activee;

    public JeuDemonstrationService(TraceDemonstrationRepository traces,
            FermierRepository fermiers, FermeRepository fermes, SiteRepository sites,
            RucheRepository ruches, AgentRepository agents, VisiteRepository visites,
            @Value("${zumm.demonstration.activee:false}") boolean activee) {
        this.traces = traces;
        this.fermiers = fermiers;
        this.fermes = fermes;
        this.sites = sites;
        this.ruches = ruches;
        this.agents = agents;
        this.visites = visites;
        this.activee = activee;
    }

    @Transactional(readOnly = true)
    public EtatDemonstration etat() {
        return new EtatDemonstration(activee, traces.count() > 0, (int) traces.count());
    }

    /**
     * Charge le jeu : un fermier, une ferme, un agent, deux ruchers, cinq ruches
     * et une visite par ruche.
     *
     * <p>Volontairement <strong>petit</strong>. Une démonstration sert à
     * comprendre la forme du produit en trois minutes ; trente ruches et deux
     * ans d'historique donneraient un écran impressionnant et illisible, et une
     * purge qu'on n'ose plus lancer.
     */
    public EtatDemonstration charger() {
        exigerActivee();
        if (traces.count() > 0) {
            throw new RegleMetierViolee(
                    "Un jeu de démonstration est déjà chargé. Retirez-le avant d'en charger un autre.");
        }

        Fermier fermier = tracer(TraceDemonstration.Entite.FERMIER,
                fermiers.save(new Fermier(MARQUE + "Coopérative du Causse", "demo@zumm.local")),
                Fermier::getId);
        Ferme ferme = tracer(TraceDemonstration.Entite.FERME,
                fermes.save(new Ferme(MARQUE + "Ferme des tilleuls", fermier)), Ferme::getId);
        Agent agent = agents.save(new Agent(MARQUE + "Amine Trabelsi", RoleAgent.APICULTEUR, ferme));
        agent.setEmail(null);
        // Un agent de démonstration ne reçoit pas de courriel : il n'a pas
        // d'adresse, et il ne doit pas non plus en attendre une.
        agent.setNotificationsEmail(false);
        tracer(TraceDemonstration.Entite.AGENT, agent, Agent::getId);

        Site plateau = rucher(ferme, "Rucher du plateau", "44.8000", "1.8000", "Gramat", "faible");
        Site vallee = rucher(ferme, "Rucher de la vallée", "44.4470", "1.4410", "Figeac", "bonne");

        LocalDate jour = LocalDate.now();
        peupler(plateau, ferme, agent, 3, jour);
        peupler(vallee, ferme, agent, 2, jour);

        log.info("Jeu de démonstration chargé : {} objets tracés.", traces.count());
        return etat();
    }

    /**
     * Retire le jeu, et rien d'autre.
     *
     * <p>L'ordre suit {@code TraceDemonstration.Entite} : la ruche d'abord —
     * elle casse en cascade ses visites, tâches, récoltes et mesures —, puis le
     * rucher, l'agent, la ferme et le fermier, qui sont en {@code ON DELETE
     * RESTRICT} et refuseraient de partir les premiers.
     *
     * <p>Une trace dont la cible a déjà disparu (l'utilisateur a supprimé la
     * ruche à la main) est simplement oubliée : le but est que la démonstration
     * ne soit plus là, pas qu'on puisse prouver qui l'a effacée.
     */
    public EtatDemonstration purger() {
        exigerActivee();
        List<TraceDemonstration> aRetirer = traces.findAllByOrderByOrdreAscEntiteIdDesc();
        for (TraceDemonstration trace : aRetirer) {
            supprimerCible(trace);
        }
        traces.deleteAll(aRetirer);
        log.info("Jeu de démonstration retiré : {} objets.", aRetirer.size());
        return etat();
    }

    // ─── Fabrication ─────────────────────────────────────────────────────────

    private Site rucher(Ferme ferme, String nom, String latitude, String longitude, String ville,
            String couverture) {
        Site site = new Site(MARQUE + nom, ferme, new BigDecimal(latitude),
                new BigDecimal(longitude), LocalDate.now().minusYears(1));
        site.setVille(ville);
        site.setPays("FR");
        site.setTypeSite("sedentaire");
        // La couverture réseau fait partie de la démonstration : c'est elle qui
        // explique l'emport hors ligne du SPRINT-24, invisible autrement.
        site.setCouvertureReseau(couverture);
        return tracer(TraceDemonstration.Entite.SITE, sites.save(site), Site::getId);
    }

    private void peupler(Site site, Ferme ferme, Agent agent, int nombre, LocalDate jour) {
        for (int rang = 1; rang <= nombre; rang++) {
            Ruche ruche = new Ruche(MARQUE + "Dadant 10 cadres", site, ferme, EtatRuche.ACTIVE);
            ruche.setAgentResponsable(agent);
            ruche.setTypeRuche("dadant");
            ruche.ajouterCompartiment(new Compartiment(TypeCompartiment.CORPS, 10));
            if (rang == 1) {
                // Une hausse sur la première : la démonstration doit montrer que
                // la composition varie, sinon elle laisse croire à une fiche figée.
                ruche.ajouterCompartiment(new Compartiment(TypeCompartiment.HAUSSE, 9));
            }
            tracer(TraceDemonstration.Entite.RUCHE, ruches.save(ruche), Ruche::getId);

            Visite visite = new Visite(ruche, agent, jour.minusDays(7L * rang),
                    RaisonVisite.CONTROLE);
            visite.setConstatations("Colonie visitée lors de la tournée de démonstration.");
            visites.save(visite);
            // La visite n'est PAS tracée : elle disparaît en cascade avec sa
            // ruche. Tracer ce que la base efface déjà donnerait une purge qui
            // supprime deux fois — et échoue la seconde.
        }
    }

    private <T> T tracer(TraceDemonstration.Entite entite, T objet,
            java.util.function.Function<T, Long> identifiant) {
        traces.save(new TraceDemonstration(entite, identifiant.apply(objet)));
        return objet;
    }

    private void supprimerCible(TraceDemonstration trace) {
        TraceDemonstration.Entite entite = TraceDemonstration.Entite.de(trace.getEntite());
        if (entite == null) {
            log.warn("Trace de démonstration inconnue ignorée : {}", trace.getEntite());
            return;
        }
        Long id = trace.getEntiteId();
        switch (entite) {
            case RUCHE -> ruches.findById(id).ifPresent(ruches::delete);
            case SITE -> sites.findById(id).ifPresent(sites::delete);
            case AGENT -> agents.findById(id).ifPresent(agents::delete);
            case FERME -> fermes.findById(id).ifPresent(fermes::delete);
            case FERMIER -> fermiers.findById(id).ifPresent(fermiers::delete);
            default -> log.warn("Entité de démonstration non gérée : {}", entite);
        }
    }

    private void exigerActivee() {
        if (!activee) {
            // 409 et non 404 : la route existe, c'est le déploiement qui la
            // ferme. Le dire évite de chercher une faute de frappe dans l'URL.
            throw new RegleMetierViolee(
                    "Le jeu de démonstration est désactivé sur ce déploiement "
                            + "(zumm.demonstration.activee).");
        }
    }
}
