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
import com.zumm.web.dto.BilanExploitation;
import com.zumm.web.dto.ComparaisonSaisons;
import com.zumm.web.dto.RentabiliteRuche;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Bilan annuel en PDF (SPRINT-27, lot E).
 *
 * <p>Ferme la ligne « bilan annuel PDF » du §7 — « absent » — et la seconde
 * moitié du contournement du §13, que cinq éditeurs sur douze conseillent :
 * « exportez en CSV/PDF chaque mois, ou en fin de saison ».
 *
 * <p><strong>Un document qu'on archive, pas un tableau de bord.</strong> Il
 * porte une année civile close, ses chiffres ne bougent plus, et il tient sur
 * deux pages : production, dépenses par poste, rentabilité ruche par ruche.
 * Y ajouter des courbes ferait un rapport qu'on regarde ; celui-ci est un
 * rapport qu'on range — avec la déclaration de ruchers, chez le comptable, ou
 * dans un dossier de contrôle.
 *
 * <p><strong>Il dit ce qu'il ne sait pas.</strong> Les recettes sont une
 * valorisation au prix de {@code ConfigZumm.ini}, pas un chiffre d'affaires ; et
 * les dépenses non affectées figurent séparément, sans être réparties. Un pied
 * de page le rappelle, parce qu'un PDF survit à la conversation qui l'a produit.
 */
@Service
public class BilanAnnuelPdfService {

    private static final Color MIEL = new Color(0xD9, 0xA5, 0x21);
    private static final Color VERT_ARDOISE = new Color(0x2C, 0x4A, 0x42);
    private static final Color TRAIT = new Color(0xE5, 0xE5, 0xE5);

    /**
     * Produit le bilan d'une année.
     *
     * @param saison saison correspondante, si elle est connue — elle apporte le
     *               rendement par ruche productive, que le bilan économique
     *               n'a pas
     */
    public byte[] generer(int annee, BilanExploitation bilan, ComparaisonSaisons saison) {
        Document doc = new Document(PageSize.A4, 48, 48, 54, 48);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, sortie);
        doc.open();

        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, VERT_ARDOISE);
        Font soustitre = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
        Font section = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, MIEL.darker());

        doc.add(new Paragraph("Zümm — Bilan de la saison %d".formatted(annee), titre));
        doc.add(new Paragraph("Période du %s au %s".formatted(bilan.debut(), bilan.fin()),
                soustitre));
        doc.add(espace());

        doc.add(section("Production", section));
        doc.add(production(bilan, saison));
        doc.add(espace());

        doc.add(section("Dépenses par poste", section));
        doc.add(postes(bilan.parCategorie()));
        doc.add(espace());

        doc.add(section("Résultat", section));
        doc.add(resultat(bilan));
        doc.add(espace());

        doc.add(section("Rentabilité par ruche", section));
        doc.add(parRuche(bilan.parRuche()));

        Paragraph avertissement = new Paragraph(
                "Les recettes sont une VALORISATION de la production de miel au prix "
                        + "paramétré dans ConfigZumm.ini, et non un chiffre d'affaires : Zümm "
                        + "ne connaît pas les prix de vente. Les dépenses non affectées à une "
                        + "ruche (%s €) ne sont pas réparties — une assurance ne se divise pas "
                        + "par le nombre de colonies."
                        .formatted(bilan.depensesNonAffectees()),
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
        avertissement.setSpacingBefore(20);
        doc.add(avertissement);

        doc.add(new Paragraph("Document produit le " + LocalDate.now() + " par la console Zümm.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY)));

        doc.close();
        return sortie.toByteArray();
    }

    private PdfPTable production(BilanExploitation bilan, ComparaisonSaisons saison) {
        PdfPTable table = deuxColonnes();
        ligne(table, "Miel récolté", bilan.productionMielKg() + " kg");
        if (saison != null) {
            ligne(table, "Ruches productives", String.valueOf(saison.ruchesProductives()));
            // Le rendement peut être nul : aucune ruche n'a produit. Écrire
            // « 0 kg » ferait croire à une saison catastrophique là où il n'y a
            // rien eu de saisi.
            ligne(table, "Rendement par ruche productive",
                    saison.rendementKg() == null ? "non évalué" : saison.rendementKg() + " kg");
            ligne(table, "Récoltes enregistrées", String.valueOf(saison.nombreRecoltes()));
            for (ComparaisonSaisons.ProduitSaison produit : saison.parProduit()) {
                if (!"miel".equals(produit.typeProduit())) {
                    ligne(table, "Autre production — " + produit.typeProduit(),
                            produit.quantite() + " " + produit.unite());
                }
            }
        }
        return table;
    }

    private PdfPTable postes(Map<String, BigDecimal> parCategorie) {
        PdfPTable table = deuxColonnes();
        if (parCategorie.isEmpty()) {
            ligne(table, "Aucune dépense enregistrée", "—");
            return table;
        }
        parCategorie.forEach((categorie, montant) -> ligne(table, categorie, montant + " €"));
        return table;
    }

    private PdfPTable resultat(BilanExploitation bilan) {
        PdfPTable table = deuxColonnes();
        ligne(table, "Recettes (valorisation)", bilan.recettesEur() + " €");
        ligne(table, "Dépenses", bilan.depensesEur() + " €");
        ligne(table, "dont non affectées", bilan.depensesNonAffectees() + " €");
        ligne(table, "Résultat", bilan.resultatEur() + " €");
        return table;
    }

    private PdfPTable parRuche(List<RentabiliteRuche> lignes) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new int[] {3, 3, 2, 2, 2});
        Font entete = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String colonne : List.of("Ruche", "Rucher", "Miel (kg)", "Dépenses", "Résultat")) {
            PdfPCell cellule = new PdfPCell(new Phrase(colonne, entete));
            cellule.setBackgroundColor(VERT_ARDOISE);
            cellule.setPadding(5);
            table.addCell(cellule);
        }
        for (RentabiliteRuche ligne : lignes) {
            cellule(table, ligne.rucheModele());
            cellule(table, ligne.siteNom() == null ? "—" : ligne.siteNom());
            cellule(table, String.valueOf(ligne.productionKg()));
            cellule(table, ligne.depensesEur() + " €");
            cellule(table, ligne.resultatEur() + " €");
        }
        return table;
    }

    private void cellule(PdfPTable table, String valeur) {
        PdfPCell cellule = new PdfPCell(new Phrase(valeur, corps()));
        cellule.setPadding(5);
        cellule.setBorderColor(TRAIT);
        table.addCell(cellule);
    }

    private PdfPTable deuxColonnes() {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new int[] {2, 3});
        return table;
    }

    private void ligne(PdfPTable table, String cle, String valeur) {
        Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, VERT_ARDOISE);
        PdfPCell cCle = new PdfPCell(new Phrase(cle, gras));
        PdfPCell cVal = new PdfPCell(new Phrase(valeur == null ? "—" : valeur, corps()));
        for (PdfPCell c : new PdfPCell[] {cCle, cVal}) {
            c.setBorderColor(TRAIT);
            c.setPadding(6);
        }
        table.addCell(cCle);
        table.addCell(cVal);
    }

    private Paragraph section(String titre, Font font) {
        Paragraph p = new Paragraph(titre, font);
        p.setSpacingAfter(6);
        return p;
    }

    private Paragraph espace() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(4);
        return p;
    }

    private Font corps() {
        return FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    }
}
