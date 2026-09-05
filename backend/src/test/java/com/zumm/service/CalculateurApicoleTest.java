package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zumm.web.RequeteInvalide;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Calculateurs apicoles (SPRINT-27, lot E).
 *
 * <p>Des fonctions pures : elles se testent sans base, sans contexte Spring et
 * sans horloge. C'est ce qui rend légitime d'en écrire beaucoup — et ce qui rend
 * inexcusable de ne pas les tester.
 */
class CalculateurApicoleTest {

    @Test
    @DisplayName("le sirop 1:1 partage la masse entre sucre et eau")
    void sirop11() {
        var sirop = CalculateurApicole.sirop("1:1", new BigDecimal("10"));

        // 10 L à 1,23 kg/L = 12,3 kg, moitié-moitié.
        assertThat(sirop.sucreKg()).isEqualByComparingTo("6.15");
        assertThat(sirop.eauL()).isEqualByComparingTo("6.15");
        assertThat(sirop.usage()).isEqualTo("stimulation");
    }

    @Test
    @DisplayName("le sirop 2:1 est plus dense, et destiné à l'hivernage")
    void sirop21() {
        var sirop = CalculateurApicole.sirop("2:1", new BigDecimal("10"));

        // Deux tiers de sucre : se tromper ici donne un sirop trop clair à
        // l'automne, que les abeilles devront assécher.
        assertThat(sirop.sucreKg()).isGreaterThan(sirop.eauL());
        assertThat(sirop.usage()).isEqualTo("hivernage");
    }

    @Test
    @DisplayName("une proportion inconnue est refusée, et un volume nul aussi")
    void siropRefuse() {
        assertThatThrownBy(() -> CalculateurApicole.sirop("3:1", BigDecimal.TEN))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("1:1");
        assertThatThrownBy(() -> CalculateurApicole.sirop("1:1", BigDecimal.ZERO))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> CalculateurApicole.sirop("1:1", null))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("la valorisation donne un montant ET un nombre de pots")
    void valorisation() {
        var valeur = CalculateurApicole.valoriser(new BigDecimal("12.5"), new BigDecimal("12"));

        assertThat(valeur.totalEur()).isEqualByComparingTo("150.00");
        // Les pots de 500 g sont le conditionnement courant : donner leur nombre
        // évite un calcul mental que tout le monde refait.
        assertThat(valeur.pots500g()).isEqualTo(25);
    }

    @Test
    @DisplayName("une quantité ou un prix négatif est refusé")
    void valorisationRefusee() {
        assertThatThrownBy(() ->
                CalculateurApicole.valoriser(new BigDecimal("-1"), BigDecimal.TEN))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() ->
                CalculateurApicole.valoriser(BigDecimal.TEN, new BigDecimal("-1")))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> CalculateurApicole.valoriser(null, BigDecimal.TEN))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("sur une ancre de la table, l'indice donne le taux tabulé")
    void refractometreSurAncre() {
        // 1,4915 à 20 °C : 18 % d'eau, seuil exact au-delà duquel un miel
        // fermente en pot. Se tromper d'un point ici décide mal d'une récolte.
        var mesure = CalculateurApicole.humiditeMiel(new BigDecimal("1.4915"), null);

        assertThat(mesure.humiditePct()).isEqualByComparingTo("18.0");
        assertThat(mesure.verdict()).isEqualTo("stable");
        assertThat(mesure.conformeNorme()).isTrue();
    }

    @Test
    @DisplayName("entre deux ancres, la table s'interpole")
    void refractometreInterpole() {
        // Entre 1,4940 (17 %) et 1,4915 (18 %), à peu près à mi-chemin.
        var mesure = CalculateurApicole.humiditeMiel(new BigDecimal("1.4928"), null);

        assertThat(mesure.humiditePct()).isEqualByComparingTo("17.5");
    }

    @Test
    @DisplayName("une lecture au frais décrit un miel plus humide qu'elle n'en a l'air")
    void refractometreCorrigeLaTemperature() {
        var chaud = CalculateurApicole.humiditeMiel(new BigDecimal("1.4915"),
                new BigDecimal("20"));
        var froid = CalculateurApicole.humiditeMiel(new BigDecimal("1.4915"),
                new BigDecimal("10"));

        // L'indice monte quand la température baisse : à indice égal, la lecture
        // faite au frais correspond à un miel plus humide. Négliger la
        // correction ferait passer pour stable un miel qui fermentera.
        assertThat(froid.humiditePct()).isGreaterThan(chaud.humiditePct());
        assertThat(froid.verdict()).isEqualTo("risque_fermentation");
    }

    @Test
    @DisplayName("au-delà de 20 % d'eau, le miel sort de la norme")
    void refractometreHorsNorme() {
        var mesure = CalculateurApicole.humiditeMiel(new BigDecimal("1.4850"), null);

        assertThat(mesure.humiditePct()).isEqualByComparingTo("20.6");
        assertThat(mesure.verdict()).isEqualTo("hors_norme");
        assertThat(mesure.conformeNorme()).isFalse();
    }

    @Test
    @DisplayName("hors de la table, rien n'est rendu plutôt qu'un chiffre extrapolé")
    void refractometreRefuseDExtrapoler() {
        // Un chiffre faux sur la mesure qui décide de la conservation du miel
        // serait cru : mieux vaut ne rien rendre.
        assertThatThrownBy(() ->
                CalculateurApicole.humiditeMiel(new BigDecimal("1.4700"), null))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Chataway");
        assertThatThrownBy(() ->
                CalculateurApicole.humiditeMiel(new BigDecimal("1.5200"), null))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> CalculateurApicole.humiditeMiel(null, null))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("une température de mesure aberrante est refusée")
    void refractometreTemperatureAberrante() {
        // Au-delà de ces bornes, la correction linéaire ne vaut plus — et une
        // mesure faite a 60 °C n'a de toute facon pas ete faite sur du miel.
        assertThatThrownBy(() -> CalculateurApicole.humiditeMiel(
                new BigDecimal("1.4915"), new BigDecimal("60")))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("température");
        assertThatThrownBy(() -> CalculateurApicole.humiditeMiel(
                new BigDecimal("1.4915"), new BigDecimal("-5")))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("une quantité nulle vaut zéro euro, et ce n'est pas une erreur")
    void productionNulle() {
        // Zéro kilo est un cas normal — une ruche qui n'a rien donné —, pas une
        // saisie fautive.
        var valeur = CalculateurApicole.valoriser(BigDecimal.ZERO, new BigDecimal("12"));

        assertThat(valeur.totalEur()).isEqualByComparingTo("0.00");
        assertThat(valeur.pots500g()).isZero();
    }
}
