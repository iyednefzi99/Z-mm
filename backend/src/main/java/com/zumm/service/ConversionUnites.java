package com.zumm.service;

import com.zumm.web.RequeteInvalide;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;
import org.springframework.stereotype.Service;

/**
 * Conversion d'unites heterogenes vers une unite de reference (US-019).
 *
 * <p>Permet de comparer des mesures exprimees dans des unites differentes, comme
 * l'exige le dictionnaire (« conversion d'unites vers l'unite de reference »).
 * Chaque unite sait se convertir vers la reference de sa famille et en revenir ;
 * la conversion passe donc toujours par la reference. Deux familles sont posees —
 * masse (reference kg) et temperature (reference degre Celsius) — extensibles.
 */
@Service
public class ConversionUnites {

    /** Definition d'une unite : sa famille et ses conversions vers/depuis la reference. */
    private record Unite(String famille, DoubleUnaryOperator versReference, DoubleUnaryOperator depuisReference) {
    }

    private static Unite masse(double facteurVersKg) {
        return new Unite("masse", x -> x * facteurVersKg, r -> r / facteurVersKg);
    }

    private static final Map<String, Unite> UNITES = Map.ofEntries(
            Map.entry("mg", masse(1e-6)),
            Map.entry("g", masse(1e-3)),
            Map.entry("kg", masse(1.0)),
            Map.entry("t", masse(1000.0)),
            Map.entry("lb", masse(0.453_592_37)),
            // Temperature : conversions affines (offset), reference = Celsius.
            Map.entry("c", new Unite("temperature", x -> x, r -> r)),
            Map.entry("f", new Unite("temperature", x -> (x - 32) * 5 / 9, r -> r * 9 / 5 + 32)),
            Map.entry("k", new Unite("temperature", x -> x - 273.15, r -> r + 273.15)));

    /**
     * Convertit {@code valeur} de l'unite {@code de} vers l'unite {@code vers}.
     *
     * @throws RequeteInvalide si une unite est inconnue ou si les familles different
     */
    public double convertir(double valeur, String de, String vers) {
        Unite source = unite(de);
        Unite cible = unite(vers);
        if (!source.famille().equals(cible.famille())) {
            throw new RequeteInvalide(
                    "Unités incompatibles : " + de + " (" + source.famille() + ") et "
                            + vers + " (" + cible.famille() + ").");
        }
        double enReference = source.versReference().applyAsDouble(valeur);
        return cible.depuisReference().applyAsDouble(enReference);
    }

    private Unite unite(String code) {
        Unite unite = code == null ? null : UNITES.get(code.toLowerCase());
        if (unite == null) {
            throw new RequeteInvalide("Unité inconnue : " + code);
        }
        return unite;
    }
}
