package com.zumm.service;

/**
 * Ordonnancement d'une tournee de visites (US-047, SPRINT-10).
 *
 * <p>Le probleme est celui du voyageur de commerce sur un chemin ouvert : il est
 * NP-difficile, et cette classe ne pretend pas le resoudre. Elle applique deux
 * heuristiques classiques et bon marche :
 *
 * <ol>
 *   <li><b>plus proche voisin</b> — depuis le point de depart, aller a chaque fois au
 *       site non visite le plus proche. Rapide, mais l'ordre obtenu peut contenir de
 *       longs croisements ;
 *   <li><b>2-opt</b> — tant qu'inverser un segment du chemin le raccourcit, l'inverser.
 *       C'est ce qui defait les croisements laisses par l'etape precedente.
 * </ol>
 *
 * <p>Le resultat est un bon ordre, pas l'ordre optimal — et c'est assume : sur une
 * tournee d'une journee, l'ecart a l'optimum est marginal devant l'incertitude des
 * distances elles-memes, qui sont a vol d'oiseau et non routieres.
 *
 * <p>La classe ne connait que des distances : elle ne depend ni de PostGIS, ni de
 * JPA, ni du modele metier. C'est ce qui la rend testable sur des cas dont l'optimum
 * est connu a la main.
 */
public final class OptimiseurTournee {

    /** Garde-fou : 2-opt converge en pratique en quelques passes, jamais en 100. */
    private static final int PASSES_MAX = 100;

    private OptimiseurTournee() {
    }

    /**
     * Ordre de parcours des points, en partant de {@code depart}.
     *
     * @param distances matrice carree et symetrique des distances, en metres
     * @param depart    indice du point de depart
     * @return les indices des points, dans l'ordre de visite ; contient chaque indice
     *         une fois et une seule
     */
    public static int[] ordonner(double[][] distances, int depart) {
        int taille = distances.length;
        if (taille == 0) {
            return new int[0];
        }
        if (depart < 0 || depart >= taille) {
            throw new IllegalArgumentException("Point de depart hors de la matrice : " + depart);
        }
        if (taille <= 2) {
            return taille == 1 ? new int[] {0} : new int[] {depart, 1 - depart};
        }
        return ameliorer(distances, plusProcheVoisin(distances, depart));
    }

    /** Longueur totale du chemin ouvert {@code ordre}. */
    public static double longueur(double[][] distances, int[] ordre) {
        double totale = 0;
        for (int i = 1; i < ordre.length; i++) {
            totale += distances[ordre[i - 1]][ordre[i]];
        }
        return totale;
    }

    private static int[] plusProcheVoisin(double[][] distances, int depart) {
        int taille = distances.length;
        boolean[] visite = new boolean[taille];
        int[] ordre = new int[taille];
        ordre[0] = depart;
        visite[depart] = true;

        for (int rang = 1; rang < taille; rang++) {
            int courant = ordre[rang - 1];
            int meilleur = -1;
            double meilleureDistance = Double.MAX_VALUE;
            for (int candidat = 0; candidat < taille; candidat++) {
                if (!visite[candidat] && distances[courant][candidat] < meilleureDistance) {
                    meilleureDistance = distances[courant][candidat];
                    meilleur = candidat;
                }
            }
            ordre[rang] = meilleur;
            visite[meilleur] = true;
        }
        return ordre;
    }

    /**
     * 2-opt sur chemin ouvert : inverser le segment {@code [i, j]} remplace les aretes
     * {@code (i-1, i)} et {@code (j, j+1)} par {@code (i-1, j)} et {@code (i, j+1)}.
     * Le depart reste en tete (i commence a 1) ; quand {@code j} est le dernier point,
     * l'arete sortante n'existe pas — son cout est nul des deux cotes.
     */
    private static int[] ameliorer(double[][] distances, int[] ordre) {
        int taille = ordre.length;
        boolean progresse = true;

        for (int passe = 0; passe < PASSES_MAX && progresse; passe++) {
            progresse = false;
            for (int i = 1; i < taille - 1; i++) {
                for (int j = i + 1; j < taille; j++) {
                    boolean dernier = j == taille - 1;
                    double avant = distances[ordre[i - 1]][ordre[i]]
                            + (dernier ? 0 : distances[ordre[j]][ordre[j + 1]]);
                    double apres = distances[ordre[i - 1]][ordre[j]]
                            + (dernier ? 0 : distances[ordre[i]][ordre[j + 1]]);
                    if (apres < avant - 1e-9) {
                        inverser(ordre, i, j);
                        progresse = true;
                    }
                }
            }
        }
        return ordre;
    }

    private static void inverser(int[] ordre, int debut, int fin) {
        while (debut < fin) {
            int tampon = ordre[debut];
            ordre[debut] = ordre[fin];
            ordre[fin] = tampon;
            debut++;
            fin--;
        }
    }
}
