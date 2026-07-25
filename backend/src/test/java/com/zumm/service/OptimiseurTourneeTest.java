package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires de l'ordonnancement de tournee (US-047, SPRINT-10).
 *
 * <p>Les cas sont construits pour que l'optimum se calcule a la main : des points
 * alignes sur un axe, ou aux coins d'un carre. On peut donc verifier la longueur
 * exacte du chemin, et pas seulement qu'il « a l'air court ».
 */
class OptimiseurTourneeTest {

    /** Matrice des distances entre points d'un axe : |x_i - x_j|. */
    private static double[][] surUnAxe(double... positions) {
        int taille = positions.length;
        double[][] distances = new double[taille][taille];
        for (int i = 0; i < taille; i++) {
            for (int j = 0; j < taille; j++) {
                distances[i][j] = Math.abs(positions[i] - positions[j]);
            }
        }
        return distances;
    }

    @Test
    @DisplayName("sur des points alignes, part du depart et balaie sans revenir sur ses pas")
    void pointsAlignes() {
        // 0 ─ 10 ─ 20 ─ 30, depart a gauche : l'optimum est le balayage, soit 30.
        double[][] distances = surUnAxe(0, 10, 20, 30);

        int[] ordre = OptimiseurTournee.ordonner(distances, 0);

        assertThat(ordre).containsExactly(0, 1, 2, 3);
        assertThat(OptimiseurTournee.longueur(distances, ordre)).isEqualTo(30);
    }

    @Test
    @DisplayName("2-opt rattrape le piege du plus proche voisin (optimum connu : 8)")
    void rattrapeLeChoixGloutonQuiCoutePlusCher() {
        // Positions : A=0 (depart), B=1, C=-2, D=4.
        // Plus proche voisin seul : A→B(1) puis B→C(3) puis C→D(6) = 10.
        // Optimum : A→C(2) puis C→B(3) puis B→D(3) = 8.
        double[][] distances = surUnAxe(0, 1, -2, 4);

        int[] ordre = OptimiseurTournee.ordonner(distances, 0);

        assertThat(OptimiseurTournee.longueur(distances, ordre)).isEqualTo(8);
        assertThat(ordre).containsExactly(0, 2, 1, 3);
    }

    @Test
    @DisplayName("sur les quatre coins d'un carre, longe le perimetre (optimum connu : 30)")
    void coinsDunCarre() {
        // (0,0) (0,10) (10,10) (10,0) : le chemin ouvert optimal fait trois cotes.
        double[][] distances = {
            {0, 10, 14.142135623730951, 10},
            {10, 0, 10, 14.142135623730951},
            {14.142135623730951, 10, 0, 10},
            {10, 14.142135623730951, 10, 0},
        };

        int[] ordre = OptimiseurTournee.ordonner(distances, 0);

        assertThat(OptimiseurTournee.longueur(distances, ordre)).isEqualTo(30);
        assertThat(ordre[0]).isZero();
    }

    @Test
    @DisplayName("le depart impose est toujours la premiere etape")
    void respecteLeDepartImpose() {
        double[][] distances = surUnAxe(0, 10, 20, 30);

        for (int depart = 0; depart < 4; depart++) {
            assertThat(OptimiseurTournee.ordonner(distances, depart)[0]).isEqualTo(depart);
        }
    }

    @Test
    @DisplayName("chaque site apparait une fois et une seule")
    void ordreEstUnePermutation() {
        double[][] distances = surUnAxe(0, 3, -7, 12, 5, -1, 9);

        int[] ordre = OptimiseurTournee.ordonner(distances, 3);

        int[] trie = ordre.clone();
        Arrays.sort(trie);
        assertThat(trie).containsExactly(0, 1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("les tournees triviales (0 ou 1 site) ne font pas exploser le calcul")
    void tourneesTriviales() {
        assertThat(OptimiseurTournee.ordonner(new double[0][0], 0)).isEmpty();
        assertThat(OptimiseurTournee.ordonner(new double[][] {{0}}, 0)).containsExactly(0);
        assertThat(OptimiseurTournee.longueur(new double[][] {{0}}, new int[] {0})).isZero();
    }

    @Test
    @DisplayName("a deux sites, l'ordre part du depart quel qu'il soit")
    void deuxSites() {
        double[][] distances = surUnAxe(0, 7);

        assertThat(OptimiseurTournee.ordonner(distances, 0)).containsExactly(0, 1);
        assertThat(OptimiseurTournee.ordonner(distances, 1)).containsExactly(1, 0);
    }

    @Test
    @DisplayName("un depart hors de la matrice est refuse plutot que silencieusement corrige")
    void departInvalide() {
        double[][] distances = surUnAxe(0, 10, 20);

        assertThatThrownBy(() -> OptimiseurTournee.ordonner(distances, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depart");
    }
}
