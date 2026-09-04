package com.zumm.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Écrivain XLSX minimal, sans dépendance (SPRINT-27, lot E).
 *
 * <p>Ferme la ligne « export XLSX » du §7 — « formats CSV/TXT uniquement ».
 *
 * <p><strong>Pourquoi pas Apache POI.</strong> POI est la bibliothèque de
 * référence, et elle est excellente ; elle pèse une douzaine de mégaoctets de
 * dépendances transitives, pour produire ici une grille de chaînes sans style,
 * sans formule et sans image. Le dépôt a déjà tranché une question de cette
 * forme — <a href="../../../../../../roadmap/operationnel/06_decisions/ADR-007-graphiques-svg.md">ADR-007</a>
 * a écarté Chart.js au profit de SVG écrit à la main, pour le poids et la
 * maîtrise. La même logique s'applique.
 *
 * <p><strong>Ce que produit cette classe, exactement.</strong> Un classeur d'une
 * feuille, dont chaque cellule est une chaîne en ligne ({@code inlineStr}), ce
 * qui évite la table des chaînes partagées et sa moitié de complexité. Cinq
 * parties suffisent, et ce sont celles que la spécification OOXML rend
 * obligatoires : les types de contenu, la relation racine, le classeur, sa
 * relation vers la feuille, et la feuille.
 *
 * <p><strong>Ce qu'elle ne produit pas</strong>, et qu'il ne faut pas lui
 * demander : ni format de nombre, ni date typée, ni largeur de colonne, ni
 * plusieurs feuilles. Le jour où l'une de ces trois manque vraiment, POI devient
 * le bon choix — et ce commentaire est là pour que la question se repose plutôt
 * que de faire grossir ce fichier.
 */
public final class ClasseurXlsx {

    /** Type MIME officiel du format, à servir tel quel. */
    public static final String TYPE_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ClasseurXlsx() {
    }

    /**
     * Produit un classeur d'une feuille à partir de lignes de chaînes.
     *
     * @param nomFeuille nom de l'onglet, tel qu'Excel l'affichera
     * @param lignes     la grille, en-tête compris ; les lignes peuvent être de
     *                   longueurs différentes
     */
    public static byte[] generer(String nomFeuille, List<List<String>> lignes) {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(sortie)) {
            ecrire(zip, "[Content_Types].xml", typesDeContenu());
            ecrire(zip, "_rels/.rels", relationsRacine());
            ecrire(zip, "xl/workbook.xml", classeur(nomFeuille));
            ecrire(zip, "xl/_rels/workbook.xml.rels", relationsClasseur());
            ecrire(zip, "xl/worksheets/sheet1.xml", feuille(lignes));
        } catch (IOException impossible) {
            // Écriture en mémoire : cette branche ne s'atteint pas.
            throw new UncheckedIOException(impossible);
        }
        return sortie.toByteArray();
    }

    private static void ecrire(ZipOutputStream zip, String nom, String contenu)
            throws IOException {
        zip.putNextEntry(new ZipEntry(nom));
        zip.write(contenu.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String typesDeContenu() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>""";
    }

    private static String relationsRacine() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""";
    }

    private static String classeur(String nomFeuille) {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" \
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets><sheet name="%s" sheetId="1" r:id="rId1"/></sheets>
                </workbook>""".formatted(nomOnglet(nomFeuille));
    }

    private static String relationsClasseur() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>""";
    }

    private static String feuille(List<List<String>> lignes) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
                <sheetData>""");
        for (int i = 0; i < lignes.size(); i++) {
            xml.append("<row r=\"").append(i + 1).append("\">");
            List<String> ligne = lignes.get(i);
            for (int j = 0; j < ligne.size(); j++) {
                // `inlineStr` pour TOUT, y compris les nombres : une cellule
                // numérique demanderait de deviner ce qui est un nombre, et
                // « 08 » (un code) deviendrait 8. Le tableur convertit à la
                // demande ; l'export, lui, ne doit rien réinterpréter.
                xml.append("<c r=\"").append(reference(j, i + 1))
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(echapper(ligne.get(j)))
                        .append("</t></is></c>");
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    /** Référence de cellule : colonne en lettres, ligne en chiffres — {@code AB12}. */
    private static String reference(int colonne, int ligne) {
        StringBuilder lettres = new StringBuilder();
        int reste = colonne;
        do {
            lettres.insert(0, (char) ('A' + reste % 26));
            reste = reste / 26 - 1;
        } while (reste >= 0);
        return lettres.append(ligne).toString();
    }

    /**
     * Nom d'onglet acceptable par Excel : 31 caractères au plus, sans les sept
     * caractères qu'il refuse. Un nom invalide fait rejeter le fichier entier.
     */
    private static String nomOnglet(String nom) {
        String propre = nom.replaceAll("[\\\\/*?:\\[\\]]", "-");
        return propre.length() > 31 ? propre.substring(0, 31) : propre;
    }

    private static String echapper(String valeur) {
        if (valeur == null) {
            return "";
        }
        return valeur
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                // Les caractères de contrôle sont interdits en XML 1.0 et font
                // rejeter le classeur : une note de visite contenant un caractère
                // parasite ne doit pas rendre l'export inouvrable.
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
    }
}
