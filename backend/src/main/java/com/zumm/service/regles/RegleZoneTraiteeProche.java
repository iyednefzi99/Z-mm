package com.zumm.service.regles;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.repository.ZoneTraiteeRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Un traitement a ete declare dans le rayon de butinage d'un rucher
 * (SPRINT-33, lot K).
 *
 * <p>Ce que la regle fait, et ce qu'elle ne fait pas, tient dans une distinction
 * qui a deja servi deux fois dans ce depot :
 *
 * <ul>
 *   <li>elle <strong>signale</strong> — un traitement a ete declare, voici a
 *       quelle distance, voici quand ;
 *   <li>elle ne <strong>conclut</strong> pas — ni « vos abeilles ont ete
 *       exposees », ni « deplacez le rucher ». La declaration ne dit ni la dose,
 *       ni la derive, ni le vent ce jour-la. Affirmer l'exposition serait un
 *       verdict invente sur une question que l'apiculteur ne peut trancher qu'en
 *       regardant ses planches d'envol — exactement le refus oppose a l'analyse
 *       acoustique au SPRINT-31.
 * </ul>
 *
 * <p><strong>Une fenetre courte, et c'est ce qui rend la tache utile.</strong>
 * Un traitement declare il y a six mois n'appelle aucun geste ; declare
 * avant-hier, il appelle un coup d'oeil aux mortalites devant la ruche. La
 * fenetre est donc de quatorze jours — au-dela, la declaration reste consultable
 * a l'ecran, mais elle ne reveille plus personne.
 *
 * <p><strong>Une tache par rucher et par declaration la plus recente</strong>,
 * pas une par zone : un traitement declare parcelle par parcelle en produirait
 * dix identiques. La cle porte la date de la derniere declaration, si bien
 * qu'une NOUVELLE declaration rouvre une tache la ou une cle figee resterait
 * muette.
 */
@Component
public class RegleZoneTraiteeProche implements RegleTache {

    /**
     * Fenetre de vigilance, en jours.
     *
     * <p>Quatorze : la duree pendant laquelle une mortalite devant la ruche peut
     * encore se rattacher a l'evenement. Plus court laisserait passer une
     * declaration faite avec retard — le voisin ne previent pas toujours le jour
     * meme ; plus long remplirait la liste de rappels sans geste associe.
     */
    private static final int FENETRE_JOURS = 14;

    private final ZoneTraiteeRepository zones;
    private final ConfigurationMetier configuration;

    public RegleZoneTraiteeProche(ZoneTraiteeRepository zones,
            ConfigurationMetier configuration) {
        this.zones = zones;
        this.configuration = configuration;
    }

    @Override
    public String code() {
        return "zone-traitee-proche";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        return zones.ruchersExposes(configuration.seuils().rayonButinageKm(),
                        jour.minusDays(FENETRE_JOURS)).stream()
                .map(expose -> new TacheProposee(
                        "%s:%d:%s".formatted(code(), expose.siteId(), expose.derniere()),
                        "Traitement declare le %s a %s m de %s : surveiller les planches "
                                .formatted(expose.derniere(),
                                        expose.distanceM() == null ? "?"
                                                : expose.distanceM().toPlainString(),
                                        expose.siteNom())
                                + "d'envol",
                        null,
                        jour,
                        // Haute : la fenetre d'observation d'une mortalite se
                        // ferme en quelques jours. Critique declencherait un
                        // courriel, et un courriel par declaration de voisinage
                        // finirait en filtre.
                        "haute",
                        "controle"))
                .toList();
    }
}
