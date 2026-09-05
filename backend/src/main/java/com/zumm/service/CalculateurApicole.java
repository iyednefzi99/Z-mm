package com.zumm.service;

import com.zumm.web.RequeteInvalide;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculateurs apicoles (SPRINT-27, lot E).
 *
 * <p>Ferme le 🟡 du §6 : « restent le sirop, le prix du miel et le
 * réfractomètre ». APIGO et BuzzWise en font une batterie entière — peu coûteux
 * et très visible.
 *
 * <p><strong>Des fonctions pures, sans base ni état</strong>, comme
 * {@code ConversionUnites} qui a donné le patron au SPRINT-01. Elles se testent
 * sans contexte Spring, et c'est ce qui rend légitime d'en écrire beaucoup.
 *
 * <p><strong>Le réfractomètre, et une rectification.</strong> Le SPRINT-27
 * l'avait écarté au motif que « la table de Chataway est propre à chaque
 * appareil ». C'était confondre deux choses. La correspondance entre indice de
 * réfraction et taux d'eau est <strong>publiée</strong> et vaut pour tout miel ;
 * ce qui appartient à l'appareil, c'est son <strong>étalonnage</strong> — le
 * zéro fait à l'eau distillée ou à l'huile de calibration — et l'échelle qu'il
 * affiche. La conversion est donc légitime à condition de dire de quoi elle
 * part : un indice mesuré, une température de mesure, un appareil étalonné. Le
 * reste du raisonnement tenait : hors de la plage tabulée, cette méthode ne rend
 * rien plutôt que d'extrapoler.
 */
public final class CalculateurApicole {

    /**
     * Masse volumique du sirop 1:1, en kg/L (environ 1,23 à 20 °C).
     *
     * <p>Sert à passer du volume à préparer aux masses de sucre et d'eau. La
     * valeur est approchée : à 5 % près, ce qui est sans conséquence pour un
     * nourrissement et permet de ne pas prétendre à une précision de laboratoire.
     */
    private static final BigDecimal DENSITE_1_1 = new BigDecimal("1.23");

    /** Masse volumique du sirop 2:1, plus dense (environ 1,35 kg/L). */
    private static final BigDecimal DENSITE_2_1 = new BigDecimal("1.35");

    /**
     * Correspondance indice de réfraction à 20 °C vers taux d'eau, en pourcentage.
     *
     * <p>La table de Chataway, telle qu'elle est publiée et reprise par les
     * manuels apicoles. Les valeurs sont des ancres à un point de pourcentage ;
     * entre deux ancres, l'interpolation est linéaire, comme l'est la table
     * elle-même à cette échelle — 2,5 à 2,6 millièmes d'indice par point d'eau.
     *
     * <p><strong>Hors de cette plage, rien n'est rendu.</strong> Extrapoler
     * au-delà de 13 % ou en deçà de 21 % donnerait un chiffre sur une mesure qui
     * décide de la conservation d'une récolte — et un chiffre faux serait cru.
     */
    private static final double[][] TABLE_CHATAWAY = {
        {1.5044, 13.0}, {1.5018, 14.0}, {1.4992, 15.0}, {1.4966, 16.0}, {1.4940, 17.0},
        {1.4915, 18.0}, {1.4890, 19.0}, {1.4865, 20.0}, {1.4840, 21.0},
    };

    /**
     * Correction de l'indice par degré d'écart à 20 °C.
     *
     * <p>L'indice baisse quand la température monte. Une lecture faite au frais
     * décrit donc un miel plus sec qu'il n'est : négliger la correction ferait
     * passer pour stable un miel qui fermentera. C'est le sens du signe — on
     * RAMÈNE la lecture à 20 °C, on ne la corrige pas « vers le haut ».
     */
    private static final double CORRECTION_PAR_DEGRE = 0.00023;

    /** Au-delà, le miel fermente en pot. C'est le seuil qui décide d'une récolte. */
    private static final double SEUIL_FERMENTATION = 18.0;

    /** Plafond de la norme de commercialisation du miel dans l'Union européenne. */
    private static final double SEUIL_NORME = 20.0;

    private CalculateurApicole() {
    }

    /**
     * Sucre et eau nécessaires pour obtenir un volume de sirop.
     *
     * <p>Les proportions sont en <strong>masse</strong>, comme le veut l'usage
     * apicole : « 1:1 » signifie un kilo de sucre pour un litre d'eau, et non
     * un volume de sucre pour un volume d'eau. Se tromper là-dessus donne un
     * sirop trop clair à l'automne, quand il faut du 2:1 pour que les abeilles
     * n'aient pas à l'assécher.
     */
    public static Sirop sirop(String proportion, BigDecimal litres) {
        if (litres == null || litres.signum() <= 0) {
            throw new RequeteInvalide("Le volume de sirop doit être positif.");
        }
        boolean hivernage = "2:1".equals(proportion);
        if (!hivernage && !"1:1".equals(proportion)) {
            throw new RequeteInvalide("Proportion inconnue : attendu « 1:1 » ou « 2:1 ».");
        }
        BigDecimal densite = hivernage ? DENSITE_2_1 : DENSITE_1_1;
        BigDecimal masseTotale = litres.multiply(densite);
        // 1:1 → moitié sucre, moitié eau. 2:1 → deux tiers de sucre.
        BigDecimal partSucre = hivernage ? new BigDecimal("0.6667") : new BigDecimal("0.5");

        BigDecimal sucre = masseTotale.multiply(partSucre).setScale(2, RoundingMode.HALF_UP);
        BigDecimal eau = masseTotale.subtract(sucre).setScale(2, RoundingMode.HALF_UP);
        return new Sirop(proportion, litres, sucre, eau,
                hivernage ? "hivernage" : "stimulation");
    }

    /**
     * Valorisation d'une production de miel.
     *
     * <p>Une <strong>valorisation</strong>, pas un chiffre d'affaires : elle
     * applique un prix au kilo à une quantité. Zümm ne sait pas à quel prix le
     * miel a été vendu, et le mot choisi doit empêcher de le croire.
     */
    public static Valorisation valoriser(BigDecimal kilos, BigDecimal prixKgEur) {
        if (kilos == null || kilos.signum() < 0) {
            throw new RequeteInvalide("La quantité ne peut pas être négative.");
        }
        if (prixKgEur == null || prixKgEur.signum() < 0) {
            throw new RequeteInvalide("Le prix au kilo ne peut pas être négatif.");
        }
        BigDecimal total = kilos.multiply(prixKgEur).setScale(2, RoundingMode.HALF_UP);
        // Les pots de 500 g sont le conditionnement le plus courant : donner le
        // nombre de pots évite un calcul mental que tout le monde refait.
        int pots500 = kilos.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.DOWN)
                .intValue();
        return new Valorisation(kilos, prixKgEur, total, pots500);
    }

    /**
     * Taux d'eau d'un miel, lu au réfractomètre.
     *
     * <p>La mesure décide de la conservation : au-delà de 18 % le miel fermente
     * en pot, et un lot mis en pot à 20 % se perd en cave sans que rien ne l'ait
     * signalé. C'est pourquoi le résultat porte un verdict, et pas seulement un
     * nombre.
     *
     * <p>Deux préalables que l'écran doit rappeler, et que le code ne peut pas
     * vérifier : l'appareil doit être étalonné, et l'indice fourni doit être
     * celui qu'il affiche sur l'échelle des indices de réfraction. Un appareil
     * qui affiche directement un pourcentage d'eau n'a pas besoin de cette
     * conversion.
     *
     * @param temperatureC température de la MESURE, et non celle du local. Un
     *                     appareil thermocompensé se déclare à 20 °C
     */
    public static Refractometre humiditeMiel(BigDecimal indice, BigDecimal temperatureC) {
        if (indice == null) {
            throw new RequeteInvalide("L'indice de réfraction est obligatoire.");
        }
        double temperature = temperatureC == null ? 20.0 : temperatureC.doubleValue();
        if (temperature < 10 || temperature > 40) {
            throw new RequeteInvalide(
                    "La température de mesure doit être comprise entre 10 et 40 °C.");
        }
        // Ramener la lecture à 20 °C, température de référence de la table.
        double corrige = indice.doubleValue() + CORRECTION_PAR_DEGRE * (temperature - 20.0);

        double eau = interpoler(corrige);
        BigDecimal taux = BigDecimal.valueOf(eau).setScale(1, RoundingMode.HALF_UP);
        String verdict;
        if (eau > SEUIL_NORME) {
            verdict = "hors_norme";
        } else if (eau > SEUIL_FERMENTATION) {
            verdict = "risque_fermentation";
        } else {
            verdict = "stable";
        }
        return new Refractometre(indice, BigDecimal.valueOf(temperature),
                BigDecimal.valueOf(corrige).setScale(4, RoundingMode.HALF_UP),
                taux, verdict, eau <= SEUIL_NORME);
    }

    /**
     * Taux d'eau correspondant à un indice ramené à 20 °C.
     *
     * <p>La table décroît : un indice plus fort signifie un miel plus sec.
     */
    private static double interpoler(double indice) {
        double indiceSec = TABLE_CHATAWAY[0][0];
        double indiceHumide = TABLE_CHATAWAY[TABLE_CHATAWAY.length - 1][0];
        if (indice > indiceSec || indice < indiceHumide) {
            throw new RequeteInvalide("Indice hors de la table de Chataway : elle couvre "
                    + indiceHumide + " à " + indiceSec + ", soit 13 % à 21 % d'eau. "
                    + "Rien n'est extrapolé au-delà.");
        }
        for (int i = 0; i < TABLE_CHATAWAY.length - 1; i++) {
            double borneSeche = TABLE_CHATAWAY[i][0];
            double borneHumide = TABLE_CHATAWAY[i + 1][0];
            if (indice <= borneSeche && indice >= borneHumide) {
                double eauSeche = TABLE_CHATAWAY[i][1];
                double eauHumide = TABLE_CHATAWAY[i + 1][1];
                double part = (borneSeche - indice) / (borneSeche - borneHumide);
                return eauSeche + part * (eauHumide - eauSeche);
            }
        }
        // Inatteignable : les deux bornes ont été vérifiées plus haut.
        throw new RequeteInvalide("Indice hors table.");
    }

    /**
     * Sucre et eau d'un sirop.
     *
     * @param usage stimulation (1:1, au printemps) ou hivernage (2:1, à l'automne)
     */
    public record Sirop(String proportion, BigDecimal litres, BigDecimal sucreKg,
            BigDecimal eauL, String usage) {
    }

    /** Valorisation d'une production, et son équivalent en pots de 500 g. */
    public record Valorisation(BigDecimal kilos, BigDecimal prixKgEur, BigDecimal totalEur,
            int pots500g) {
    }

    /**
     * Taux d'eau d'un miel, et ce qu'il implique.
     *
     * @param indiceCorrige indice ramené à 20 °C — rendu pour que le calcul soit
     *                      vérifiable, et non pris sur parole
     * @param verdict       {@code stable}, {@code risque_fermentation} au-delà de
     *                      18 %, {@code hors_norme} au-delà de 20 %
     * @param conformeNorme faux au-delà de 20 % d'eau, plafond de la norme de
     *                      commercialisation du miel dans l'Union européenne
     */
    public record Refractometre(BigDecimal indice, BigDecimal temperatureC,
            BigDecimal indiceCorrige, BigDecimal humiditePct, String verdict,
            boolean conformeNorme) {
    }
}
