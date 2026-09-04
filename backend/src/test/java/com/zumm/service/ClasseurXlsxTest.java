package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Écrivain XLSX minimal (SPRINT-27, lot E).
 *
 * <p>Écrire un format de fichier à la main n'est défendable que si le test le
 * tient. Ce qui est vérifié ici est ce qu'Excel vérifie à l'ouverture : les
 * cinq parties obligatoires, la référence de chaque cellule, et l'échappement —
 * un seul caractère de contrôle fait rejeter le classeur entier.
 */
class ClasseurXlsxTest {

    private Map<String, String> parties(byte[] classeur) throws IOException {
        Map<String, String> contenus = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(classeur))) {
            for (ZipEntry entree = zip.getNextEntry(); entree != null; entree = zip.getNextEntry()) {
                contenus.put(entree.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return contenus;
    }

    @Test
    @DisplayName("le classeur porte les cinq parties que la spécification exige")
    void cinqParties() throws IOException {
        byte[] classeur = ClasseurXlsx.generer("ruches", List.of(List.of("id", "modele")));

        // Sans l'une d'elles, Excel refuse le fichier entier — et sans ce test,
        // on ne l'apprendrait qu'à l'ouverture.
        assertThat(parties(classeur)).containsOnlyKeys(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml");
    }

    @Test
    @DisplayName("les références de cellules suivent la colonne et la ligne")
    void referencesDeCellules() throws IOException {
        List<List<String>> grille = List.of(List.of("a", "b"), List.of("c", "d"));

        String feuille = parties(ClasseurXlsx.generer("f", grille)).get("xl/worksheets/sheet1.xml");

        assertThat(feuille).contains("r=\"A1\"").contains("r=\"B1\"")
                .contains("r=\"A2\"").contains("r=\"B2\"");
    }

    @Test
    @DisplayName("au-delà de la vingt-sixième colonne, la référence passe à deux lettres")
    void colonnesAuDelaDeZ() throws IOException {
        List<String> ligne = new ArrayList<>();
        for (int i = 0; i < 28; i++) {
            ligne.add("x");
        }

        String feuille = parties(ClasseurXlsx.generer("f", List.of(ligne)))
                .get("xl/worksheets/sheet1.xml");

        // Z puis AA, AB : l'erreur classique de ce calcul donne « [A » ou « @A ».
        assertThat(feuille).contains("r=\"Z1\"").contains("r=\"AA1\"").contains("r=\"AB1\"");
    }

    @Test
    @DisplayName("les caractères réservés de XML sont échappés, les caractères de contrôle retirés")
    void echappement() throws IOException {
        // `Arrays.asList` et non `List.of` : le nul est justement le cas a
        // eprouver, et `List.of` le refuse avant d'arriver au code teste.
        List<List<String>> grille = List.of(
                java.util.Arrays.asList("R & D <test>", "avantapres", null));

        String feuille = parties(ClasseurXlsx.generer("f", grille))
                .get("xl/worksheets/sheet1.xml");

        assertThat(feuille).contains("R &amp; D &lt;test&gt;");
        // Un caractère de contrôle est interdit en XML 1.0 : une note de visite
        // contenant un parasite ne doit pas rendre l'export inouvrable.
        assertThat(feuille).contains("avantapres").doesNotContain("");
    }

    @Test
    @DisplayName("un nom d'onglet trop long ou interdit est corrigé, jamais transmis tel quel")
    void nomOnglet() throws IOException {
        String classeur = parties(ClasseurXlsx.generer(
                "un nom beaucoup trop long pour un onglet Excel", List.of(List.of("a"))))
                .get("xl/workbook.xml");
        // Excel plafonne à 31 caractères ; au-delà, il refuse le fichier.
        assertThat(classeur).contains("name=\"un nom beaucoup trop long pour \"");

        String avecInterdits = parties(ClasseurXlsx.generer("a/b:c*d?e[f]", List.of(List.of("a"))))
                .get("xl/workbook.xml");
        assertThat(avecInterdits).contains("name=\"a-b-c-d-e-f-\"");
    }

    @Test
    @DisplayName("une grille vide produit un classeur valide, pas une exception")
    void grilleVide() throws IOException {
        byte[] classeur = ClasseurXlsx.generer("vide", List.of());

        // Exporter une ressource sans ligne est un cas normal — une exploitation
        // qui n'a pas encore de dépenses.
        assertThat(parties(classeur)).hasSize(5);
        assertThat(parties(classeur).get("xl/worksheets/sheet1.xml")).contains("<sheetData>");
    }
}
