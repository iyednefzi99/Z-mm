package com.zumm.service.regles;

import com.zumm.domain.Tache;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TacheRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Execute les regles et enregistre les taches qu'elles proposent (SPRINT-22).
 *
 * <p><strong>Le seul endroit qui ecrit des taches engendrees</strong>, et c'est
 * ce qui rend l'anti-doublon tenable : chaque tache porte une cle
 * ({@code carence-retrait:42}), un index unique partiel la rend unique par
 * exploitation, et le moteur ignore silencieusement ce qui existe deja. Une
 * regle qui s'executerait elle-meme aurait a reimplementer cette garde, et
 * l'oublierait un jour.
 *
 * <p><strong>Idempotent par construction.</strong> Le passer deux fois dans la
 * meme journee ne produit rien la seconde fois. C'est la propriete qui permet de
 * l'appeler depuis un planificateur, depuis une route d'API, ou les deux.
 *
 * <p><strong>Ce qu'il ne fait pas : supprimer.</strong> Une tache engendree puis
 * devenue sans objet — le traitement a ete retire — reste dans la liste jusqu'a
 * ce que l'apiculteur la coche. Effacer d'office une tache qu'il a peut-etre
 * commencee lui retirerait la main sur sa propre journee.
 */
@Service
@Transactional
public class MoteurRegles {

    private static final Logger LOG = LoggerFactory.getLogger(MoteurRegles.class);

    private final List<RegleTache> regles;
    private final TacheRepository taches;
    private final RucheRepository ruches;
    private final com.zumm.repository.MaterielRepository materiels;
    private final com.zumm.service.NotificationAlerteService notifications;

    /**
     * Les regles sont injectees en liste : ajouter une regle, c'est ajouter une
     * classe annotee {@code @Component}, sans toucher au moteur.
     */
    public MoteurRegles(List<RegleTache> regles, TacheRepository taches, RucheRepository ruches,
            com.zumm.repository.MaterielRepository materiels,
            com.zumm.service.NotificationAlerteService notifications) {
        this.regles = regles;
        this.taches = taches;
        this.ruches = ruches;
        this.materiels = materiels;
        this.notifications = notifications;
    }

    /** Execute toutes les regles au jour donne et rend les taches CREEES. */
    public List<Tache> executer(LocalDate jour) {
        List<Tache> creees = new ArrayList<>();
        for (RegleTache regle : regles) {
            for (TacheProposee proposee : regle.proposer(jour)) {
                enregistrer(regle, proposee).ifPresent(creees::add);
            }
        }
        LOG.debug("Moteur de regles : {} regle(s), {} tache(s) creee(s) au {}",
                regles.size(), creees.size(), jour);
        return creees;
    }

    private java.util.Optional<Tache> enregistrer(RegleTache regle, TacheProposee proposee) {
        // La garde applicative evite un aller-retour en erreur pour le cas
        // courant — la tache existe deja. L'index unique reste le garde-fou :
        // deux executions concurrentes passeraient toutes deux ce test.
        if (taches.existsByCleDeclencheur(proposee.cle())) {
            return java.util.Optional.empty();
        }
        Tache tache = new Tache(proposee.libelle());
        tache.setEcheance(proposee.echeance());
        tache.setPriorite(proposee.priorite());
        tache.setCategorie(proposee.categorie());
        tache.engendreePar(regle.code(), proposee.cle());
        if (proposee.rucheId() != null) {
            ruches.findById(proposee.rucheId()).ifPresent(tache::setRuche);
        }
        // Une tache peut viser un EQUIPEMENT et non une ruche (SPRINT-27) :
        // « reviser l'extracteur » ne se rattache a aucune colonie.
        if (proposee.materielId() != null) {
            materiels.findById(proposee.materielId()).ifPresent(tache::setMateriel);
        }
        Tache enregistree = taches.save(tache);
        if ("critique".equals(enregistree.getPriorite())) {
            notifications.notifierTacheCritique(enregistree);
        }
        return java.util.Optional.of(enregistree);
    }
}
