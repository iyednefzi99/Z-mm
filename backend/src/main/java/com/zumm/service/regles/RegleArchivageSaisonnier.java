package com.zumm.service.regles;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Rappel d'archivage en fin de saison (SPRINT-27, lot E).
 *
 * <p>Ferme la seconde moitie du contournement du §13 : « exportez en CSV/PDF
 * chaque mois, ou en fin de saison » — conseille par CINQ editeurs sur douze,
 * le contournement le plus repandu du dépouillement. La premiere moitie est
 * l'export integral, livre par ailleurs dans ce lot ; celle-ci est le rappel,
 * sans lequel l'export reste une fonction que personne n'ouvre.
 *
 * <p><strong>Une seule tache par an</strong>, et en novembre : la saison est
 * finie au nord, les hausses sont rentrees, et il reste l'hiver pour ressaisir
 * ce qui manque. La proposer en janvier arriverait apres les declarations ;
 * l'envoyer en aout tomberait en pleine miellee.
 *
 * <p>Aucune dependance, aucun depot : cette regle ne lit rien. C'est une regle
 * de CALENDRIER, et elle se teste avec une date.
 */
@Component
public class RegleArchivageSaisonnier implements RegleTache {

    /** Novembre : la saison est close au nord, l'hiver reste pour ressaisir. */
    private static final Month MOIS_RAPPEL = Month.NOVEMBER;

    @Override
    public String code() {
        return "archivage-saison";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        if (jour.getMonth() != MOIS_RAPPEL) {
            return List.of();
        }
        int saison = jour.getYear();
        return List.of(new TacheProposee(
                "%s:%d".formatted(code(), saison),
                "Archiver la saison %d : exporter les registres et le bilan annuel".formatted(
                        saison),
                null,
                // Fin d'annee : la tache a tout le mois de decembre devant elle.
                jour.withMonth(12).withDayOfMonth(31),
                "normale",
                "administratif"));
    }
}
