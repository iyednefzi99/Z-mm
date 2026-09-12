package com.zumm.service;

import com.zumm.domain.Recolte;
import com.zumm.domain.Visite;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.CorrelationMeteo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Correlation entre la meteo figee sur la visite et la production (SPRINT-22).
 *
 * <p>Ferme la derniere ligne du §7 de {@code docs/ECART-CONCURRENTS.md} : « la
 * correlation meteo ↔ production est debloquee, pas encore calculee ». La V19
 * fige la meteo au moment de la visite — temperature, humidite, vent, avec leur
 * source — et personne n'en tirait rien.
 *
 * <p><strong>Pourquoi la meteo FIGEE, et pas une meteo rappelee.</strong> Aller
 * rechercher le temps qu'il faisait le 12 mars six mois plus tard donnerait la
 * meteo d'une station, pas celle du rucher, et rendrait la correlation fausse
 * sans que rien ne le signale. C'est le motif meme des quatre colonnes de la V19.
 *
 * <p><strong>Ce service refuse de conclure a la place de l'apiculteur.</strong>
 * Il rend un coefficient ET la taille de l'echantillon, et il n'interprete rien
 * en dessous de {@value Coefficient#ECHANTILLON_MINIMAL} paires : sur cinq visites, un
 * coefficient de 0,8 ne dit rien du tout — c'est du bruit avec une decimale. Une
 * correlation n'est pas davantage une causalite : deux mois chauds qui coincident
 * avec une miellee d'acacia ne prouvent pas que la chaleur produit le miel.
 */
@Service
@Transactional(readOnly = true)
public class CorrelationMeteoService {

    /**
     * Fenetre entre la visite et la recolte qu'on lui rattache.
     *
     * <p>Trente jours : une hausse se remplit en quelques semaines, et au-dela le
     * temps qu'il faisait le jour de la visite n'explique plus rien.
     */
    private static final int FENETRE_JOURS = 30;

    private final VisiteRepository visites;
    private final RecolteRepository recoltes;

    public CorrelationMeteoService(VisiteRepository visites, RecolteRepository recoltes) {
        this.visites = visites;
        this.recoltes = recoltes;
    }

    /**
     * Coefficients de correlation entre chaque indicateur meteo et la recolte
     * qui a suivi, sur la periode demandee.
     */
    public List<CorrelationMeteo> calculer(LocalDate debut, LocalDate fin) {
        List<Paire> paires = apparier(debut, fin);
        return List.of(
                correlation("temperature", paires, p -> valeur(p.visite().getMeteoTemperatureC())),
                correlation("humidite", paires,
                        p -> p.visite().getMeteoHumiditePct() == null
                                ? null : p.visite().getMeteoHumiditePct().doubleValue()),
                correlation("vent", paires, p -> valeur(p.visite().getMeteoVentKmh())));
    }

    /**
     * Apparie chaque visite meteorologiquement renseignee avec ce que la ruche a
     * produit dans les trente jours suivants.
     *
     * <p>Une visite sans recolte suivante compte pour <strong>zero kilo</strong>,
     * et non pour rien : l'ecarter ne garderait que les visites suivies d'une
     * recolte, ce qui reviendrait a demander « quand il fait beau et qu'on
     * recolte, recolte-t-on beaucoup ? ». La reponse serait toujours oui.
     */
    private List<Paire> apparier(LocalDate debut, LocalDate fin) {
        List<Paire> paires = new ArrayList<>();
        for (Visite visite : visites.findByDateVisiteBetweenOrderByDateVisiteAsc(debut, fin)) {
            if (visite.getMeteoSource() == null) {
                continue;
            }
            double kilos = recoltes.findByOrderByDateRecolteDescIdDesc().stream()
                    .filter(r -> r.getRuche().getId().equals(visite.getRuche().getId()))
                    .filter(r -> !r.getDateRecolte().isBefore(visite.getDateVisite()))
                    .filter(r -> !r.getDateRecolte().isAfter(
                            visite.getDateVisite().plusDays(FENETRE_JOURS)))
                    .mapToDouble(r -> r.getQuantiteKg().doubleValue())
                    .sum();
            paires.add(new Paire(visite, kilos));
        }
        return paires;
    }

    private CorrelationMeteo correlation(String indicateur, List<Paire> paires,
            Function<Paire, Double> extraire) {
        List<double[]> retenues = new ArrayList<>();
        for (Paire paire : paires) {
            Double x = extraire.apply(paire);
            if (x != null) {
                retenues.add(new double[] {x, paire.kilos()});
            }
        }
        Coefficient.Lecture lecture = Coefficient.de(retenues);
        return new CorrelationMeteo(indicateur, lecture.coefficient(), retenues.size(),
                lecture.code());
    }

    private static Double valeur(BigDecimal valeur) {
        return valeur == null ? null : valeur.doubleValue();
    }

    /** Une visite et ce que la ruche a produit dans le mois qui l'a suivie. */
    private record Paire(Visite visite, double kilos) {
    }
}
