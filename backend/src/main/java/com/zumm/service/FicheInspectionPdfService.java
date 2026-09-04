package com.zumm.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.zumm.web.dto.RucheReponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Fiche d'inspection <strong>vierge</strong>, à emporter au rucher (SPRINT-24,
 * lot C).
 *
 * <p>Ferme la ligne « fiches d'inspection imprimables » du §10 de
 * {@code docs/ECART-CONCURRENTS.md}. {@link RapportVisitePdfService} produit un
 * compte rendu <em>après</em> la visite ; celle-ci est l'inverse — le support de
 * saisie qu'on remplit au stylo, une colonne par ruche, sur un rucher où l'on
 * porte des gants et où le téléphone n'a pas de réseau.
 *
 * <p><strong>C'est la réponse la plus économique au problème des gants</strong>,
 * et de loin : la saisie vocale demande une API navigateur, des permissions PWA
 * et un arbitrage sur l'endroit où tourne la transcription ; la feuille de papier
 * demande un tableau. Trois éditeurs conseillent d'ailleurs cette manœuvre à
 * leurs propres utilisateurs.
 *
 * <p><strong>Ce que la fiche reprend, et pourquoi.</strong> Les colonnes sont
 * celles de la grille d'inspection structurée du SPRINT-20 — œufs, couvain,
 * cellules royales, réserves, tempérament, reine vue. Inventer ici des cases que
 * le formulaire ne connaît pas produirait une feuille qu'on ne peut pas
 * ressaisir, ce qui est exactement le piège que le papier tend.
 */
@Service
public class FicheInspectionPdfService {

    private static final Color MIEL = new Color(0xD9, 0xA5, 0x21);
    private static final Color VERT_ARDOISE = new Color(0x2C, 0x4A, 0x42);
    private static final Color TRAIT = new Color(0xC8, 0xD2, 0xCE);

    /**
     * Colonnes de la grille. Elles suivent l'ordre de l'inspection réelle —
     * ce qu'on voit en ouvrant, puis ce qu'on en déduit — et non l'ordre du
     * modèle de données.
     */
    private static final List<String> COLONNES = List.of(
            "Ruche", "Œufs", "Couvain", "Cell. roy.", "Réserves", "Tempér.", "Reine vue", "Notes");

    /** Largeurs relatives : « Notes » prend ce qui reste, c'est là qu'on écrit. */
    private static final int[] LARGEURS = {14, 7, 9, 9, 9, 9, 9, 34};

    /**
     * Produit la fiche d'un rucher.
     *
     * <p>Une <strong>ligne vide de plus</strong> que de ruches connues : au
     * rucher, on trouve toujours une ruche qui n'est pas encore au fichier — un
     * essaim logé la semaine dernière, un nucleus monté d'une division. Sans
     * cette ligne, elle se note dans la marge et se perd.
     */
    public byte[] generer(String siteNom, List<RucheReponse> ruches) {
        Document doc = new Document(PageSize.A4.rotate(), 36, 36, 42, 36);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, sortie);
        doc.open();

        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, VERT_ARDOISE);
        Font soustitre = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);

        doc.add(new Paragraph("Zümm — Fiche d'inspection", titre));
        doc.add(new Paragraph("Rucher %s · %d ruche(s) · date : ____ / ____ / ________"
                .formatted(siteNom, ruches.size()), soustitre));
        doc.add(new Paragraph("Agent : ______________________________", soustitre));
        doc.add(espace());

        doc.add(grille(ruches));

        Paragraph legende = new Paragraph(
                "Œufs / Reine vue : cocher. Couvain, réserves : compter les cadres. "
                        + "Cellules royales : nombre. Tempérament : calme, nerveux, agressif.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
        legende.setSpacingBefore(10);
        doc.add(legende);

        Paragraph pied = new Paragraph(
                "Fiche vierge produite le " + LocalDate.now()
                        + " — à ressaisir dans la console au retour.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
        pied.setSpacingBefore(4);
        doc.add(pied);

        doc.close();
        return sortie.toByteArray();
    }

    private PdfPTable grille(List<RucheReponse> ruches) {
        PdfPTable table = new PdfPTable(COLONNES.size());
        table.setWidthPercentage(100);
        table.setWidths(LARGEURS);

        Font entete = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String colonne : COLONNES) {
            PdfPCell cellule = new PdfPCell(new Phrase(colonne, entete));
            cellule.setBackgroundColor(VERT_ARDOISE);
            cellule.setPadding(5);
            cellule.setBorderColor(VERT_ARDOISE);
            table.addCell(cellule);
        }

        Font corps = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        for (RucheReponse ruche : ruches) {
            // La ruche est NOMMEE : une fiche où l'apiculteur doit lui-même
            // recopier quarante identifiants ne se remplit pas.
            ligne(table, "#%d %s".formatted(ruche.id(), ruche.modele()), corps);
        }
        ligne(table, " ", corps);
        return table;
    }

    private void ligne(PdfPTable table, String libelle, Font corps) {
        PdfPCell premiere = new PdfPCell(new Phrase(libelle, corps));
        premiere.setPadding(7);
        premiere.setBorderColor(TRAIT);
        table.addCell(premiere);
        for (int i = 1; i < COLONNES.size(); i++) {
            PdfPCell vide = new PdfPCell(new Phrase(" ", corps));
            vide.setPadding(7);
            vide.setBorderColor(TRAIT);
            // La colonne « Notes » se distingue à l'œil : c'est celle qu'on
            // remplit debout, et la chercher coûte du temps sur quarante lignes.
            if (i == COLONNES.size() - 1) {
                vide.setBackgroundColor(new Color(MIEL.getRed(), MIEL.getGreen(), MIEL.getBlue(),
                        18));
            }
            table.addCell(vide);
        }
    }

    private Paragraph espace() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(2);
        return p;
    }
}
