package com.zumm.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Coefficient de correlation lineaire, et le refus de l'interpreter.
 *
 * <p>Extrait de {@code CorrelationMeteoService} au SPRINT-33, quand une seconde
 * correlation est apparue — sante des colonies contre flore environnante. Le
 * calcul lui-meme tient en quinze lignes ; ce n'est pas lui qui justifie une
 * classe commune, ce sont les <strong>trois refus</strong> qui l'entourent et qui
 * doivent rester identiques d'une correlation a l'autre :
 *
 * <ol>
 *   <li>moins de deux points : aucun coefficient n'existe ;
 *   <li>variance nulle sur l'une des series : le denominateur est nul, et rendre
 *       0 laisserait croire a une absence de lien la ou il n'y a pas de mesure ;
 *   <li>echantillon trop petit : le coefficient sort, mais <strong>nu</strong> —
 *       sur huit points, « lien marque » est une affirmation gratuite, et c'est
 *       precisement celle qu'un tableau de bord fait retenir.
 * </ol>
 *
 * <p>Les dupliquer aurait suffi a les faire deriver : le premier ajustement de
 * seuil sur une correlation aurait laisse l'autre en arriere, sans que rien ne le
 * signale — deux ecrans du meme produit auraient alors dit « lien faible » et
 * « lien modere » de la meme force de lien.
 */
final class Coefficient {

    /**
     * En dessous, aucune interpretation n'est rendue : le coefficient reste nu.
     *
     * <p>Douze, et le nombre ne veut pas dire la meme chose selon la
     * correlation : douze visites meteorologiquement renseignees se collectent en
     * une saison, douze ruchers sont deja une exploitation professionnelle. C'est
     * assume — le seuil borne le BRUIT, pas la representativite, et la taille de
     * l'echantillon sort avec le coefficient pour que le lecteur en juge.
     */
    static final int ECHANTILLON_MINIMAL = 12;

    private Coefficient() {
    }

    /** Un coefficient arrondi au centieme, et le code de lecture qui l'accompagne. */
    record Lecture(BigDecimal coefficient, String code) {
    }

    /**
     * Calcule le coefficient de Pearson sur des points {@code [x, y]}.
     *
     * @return la lecture, dont le coefficient est {@code null} des que la formule
     *         n'a pas de sens — jamais 0, qui serait une affirmation
     */
    static Lecture de(List<double[]> points) {
        int n = points.size();
        if (n < 2) {
            return new Lecture(null, "echantillon_insuffisant");
        }
        Double r = pearson(points);
        if (r == null) {
            return new Lecture(null, "variance_nulle");
        }
        return new Lecture(BigDecimal.valueOf(Math.round(r * 100) / 100.0), interpreter(r, n));
    }

    /**
     * Coefficient de Pearson, ou {@code null} si l'une des deux series est
     * constante — le denominateur serait nul, et la formule n'a alors pas de sens.
     */
    private static Double pearson(List<double[]> points) {
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
     * « lien fort » serait une affirmation gratuite.
     */
    private static String interpreter(double r, int n) {
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
}
