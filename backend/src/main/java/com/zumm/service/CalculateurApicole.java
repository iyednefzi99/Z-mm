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
 * <p><strong>Le réfractomètre n'y est pas, et c'est délibéré.</strong> Convertir
 * un indice de réfraction en taux d'humidité demande la table de correspondance
 * de Chataway, propre à chaque appareil et à sa température de calibration.
 * L'implémenter au jugé donnerait un chiffre faux sur une mesure qui décide de
 * la conservation du miel — un miel à plus de 18 % fermente. Mieux vaut ne rien
 * rendre que rendre une valeur qu'on croira exacte.
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
}
