package com.zumm.service.regles;

import com.zumm.domain.Materiel;
import com.zumm.repository.MaterielRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * L'entretien d'un equipement arrive a echeance (SPRINT-27, lot E).
 *
 * <p>Ferme la ligne « plan de maintenance du materiel : taches recurrentes
 * attachees a un equipement, pas a une ruche » du §13, deduite d'un conseil
 * d'Onibi — « nettoyez les optiques, grattez la propolis sur les glissieres ».
 *
 * <p><strong>La regle ne stocke rien.</strong> L'echeance se deduit de
 * {@code derniere_maintenance + periodicite_jours} ; ranger une date
 * « prochaine maintenance » en base creerait une valeur a maintenir en coherence
 * avec la derniere, la meme dette que {@code ComptageVarroaService} evite pour
 * le taux de varroa.
 *
 * <p><strong>La cle porte l'echeance</strong>, et c'est ce qui rend la
 * recurrence possible : une fois l'entretien fait, la date recule et la cle
 * change — le moteur reproposera la tache au terme suivant, sans jamais
 * dupliquer celle du terme courant.
 */
@Component
public class RegleMaintenanceMateriel implements RegleTache {

    private final MaterielRepository materiels;

    public RegleMaintenanceMateriel(MaterielRepository materiels) {
        this.materiels = materiels;
    }

    @Override
    public String code() {
        return "maintenance-materiel";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        List<TacheProposee> proposees = new ArrayList<>();
        for (Materiel materiel : materiels.findByPeriodiciteJoursIsNotNull()) {
            LocalDate echeance = materiel.prochaineMaintenance(jour);
            if (echeance == null || echeance.isAfter(jour)) {
                continue;
            }
            proposees.add(new TacheProposee(
                    "%s:%d:%s".formatted(code(), materiel.getId(), echeance),
                    "Entretien du materiel : %s".formatted(materiel.getLibelle()),
                    null,
                    materiel.getId(),
                    echeance,
                    // Un entretien en retard n'est pas une urgence : une balance
                    // sale mesure encore. Le passer en « haute » ferait descendre
                    // les vraies urgences d'un rang dans la liste.
                    "normale",
                    "materiel"));
        }
        return proposees;
    }
}
