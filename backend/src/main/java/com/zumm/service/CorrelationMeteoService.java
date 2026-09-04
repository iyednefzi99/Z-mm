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
 * en dessous de {@value #ECHANTILLON_MINIMAL} paires : sur cinq visites, un
 * coefficient de 0,8 ne dit rien du tout — c'est du bruit avec une decimale. Une
 * correlation n'est pas davantage une causalite : deux mois chauds qui coincident
 * avec une miellee d'acacia ne prouvent pas que la chaleur produit le miel.
 */
@Service
@Transactional(readOnly = true)
public class CorrelationMeteoService {

    /** En dessous, aucune interpretation n'est rendue : le coefficient reste nu. */
    static final int ECHANTILLON_MINIMAL = 12;

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
        int n = retenues.size();
        if (n < 2) {
            return new CorrelationMeteo(indicateur, null, n, "echantillon_insuffisant");
        }
        Double r = pearson(retenues);
        if (r == null) {
            // Variance nulle : toutes les visites au meme degre. Le coefficient
            // n'existe pas, et rendre 0 laisserait croire a une absence de lien.
            return new CorrelationMeteo(indicateur, null, n, "variance_nulle");
        }
        return new CorrelationMeteo(indicateur,
                BigDecimal.valueOf(Math.round(r * 100) / 100.0), n, interpreter(r, n));
    }

    /**
     * Coefficient de Pearson, ou {@code null} si l'une des deux series est
     * constante — le denominateur serait nul, et la formule n'a alors pas de
     * sens.
     */
    private Double pearson(List<double[]> points) {
        int n = points.size();
        double sx = 0;
        double sy = 0;
        for (double[] p : points) {
            sx += p[0];
            sy += p[1];
        }
        double mx = sx / n;
        double my = sy / n;
        double num = 0;
        double dx = 0;
        double dy = 0;
        for (double[] p : points) {
            double ex = p[0] - mx;
            double ey = p[1] - my;
            num += ex * ey;
            dx += ex * ex;
            dy += ey * ey;
        }
        double den = Math.sqrt(dx * dy);
        return den == 0 ? null : num / den;
    }

    /**
     * Traduit le coefficient, ou refuse de le traduire.
     *
     * <p>Le seuil d'echantillon vient AVANT la force du lien : sur huit paires,
     * « lien fort » serait une affirmation gratuite, et c'est precisement celle
     * qu'un tableau de bord fait retenir.
     */
    private String interpreter(double r, int n) {
        if (n < ECHANTILLON_MINIMAL) {
            return "echantillon_insuffisant";
        }
        double force = Math.abs(r);
        if (force < 0.3) {
            return "lien_faible";
        }
        String sens = r > 0 ? "positif" : "negatif";
        return (force < 0.6 ? "lien_modere_" : "lien_marque_") + sens;
    }

    private static Double valeur(BigDecimal valeur) {
        return valeur == null ? null : valeur.doubleValue();
    }

    /** Une visite et ce que la ruche a produit dans le mois qui l'a suivie. */
    private record Paire(Visite visite, double kilos) {
    }
}
