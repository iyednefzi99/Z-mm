package com.zumm.configmetier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Petit lecteur de fichiers INI, sans dependance externe.
 *
 * <p>Reconnait les sections {@code [nom]}, les paires {@code cle = valeur} et les
 * commentaires en {@code ;} ou {@code #}. Les cles sont regroupees par section ;
 * une cle hors de toute section tombe dans la section vide {@code ""}. Espaces de
 * bordure supprimes, cles insensibles a la casse (rangees en minuscules).
 *
 * <p>Le format de {@code ConfigZumm.ini} est volontairement trivial : identifiants
 * et cles ne sont pas traduits (cf. gabarit {@code config/ConfigZumm.example.ini}).
 */
final class LecteurIni {

    private LecteurIni() {
    }

    /** Analyse un flux INI en {@code section -> (cle -> valeur)}. */
    static Map<String, Map<String, String>> analyser(Reader source) throws IOException {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String sectionCourante = "";
        sections.put(sectionCourante, new LinkedHashMap<>());

        try (BufferedReader lecteur = new BufferedReader(source)) {
            String ligne;
            while ((ligne = lecteur.readLine()) != null) {
                String contenu = retirerCommentaire(ligne).trim();
                if (contenu.isEmpty()) {
                    continue;
                }
                if (contenu.startsWith("[") && contenu.endsWith("]")) {
                    sectionCourante = contenu.substring(1, contenu.length() - 1).trim().toLowerCase();
                    sections.computeIfAbsent(sectionCourante, cle -> new LinkedHashMap<>());
                    continue;
                }
                int separateur = contenu.indexOf('=');
                if (separateur < 0) {
                    continue; // Ligne sans '=' : ignoree plutot que de faire echouer le chargement.
                }
                String cle = contenu.substring(0, separateur).trim().toLowerCase();
                String valeur = contenu.substring(separateur + 1).trim();
                if (!cle.isEmpty()) {
                    sections.get(sectionCourante).put(cle, valeur);
                }
            }
        }
        return sections;
    }

    /**
     * Retire un commentaire de fin de ligne ({@code ;} ou {@code #}). Simple par
     * choix : le format ne prevoit ni echappement ni valeur contenant ces
     * caracteres.
     */
    private static String retirerCommentaire(String ligne) {
        int pointVirgule = ligne.indexOf(';');
        int diese = ligne.indexOf('#');
        int coupe = -1;
        if (pointVirgule >= 0) {
            coupe = pointVirgule;
        }
        if (diese >= 0 && (coupe < 0 || diese < coupe)) {
            coupe = diese;
        }
        return coupe < 0 ? ligne : ligne.substring(0, coupe);
    }
}
