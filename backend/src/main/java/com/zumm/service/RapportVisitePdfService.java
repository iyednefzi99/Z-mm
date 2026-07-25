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
import com.zumm.web.dto.PhotoReponse;
import com.zumm.web.dto.VisiteReponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Génère le PDF d'un rapport de visite (US-044, SPRINT-09).
 *
 * <p>Rendu serveur sans état à partir du DTO {@link VisiteReponse} déjà chargé par
 * {@link VisiteService}. Le document reprend l'identité Zümm (miel & vert) et
 * présente les métadonnées de la visite, ses évaluations et son texte libre.
 */
@Service
public class RapportVisitePdfService {

    private static final Color MIEL = new Color(0xD9, 0xA5, 0x21);
    private static final Color VERT_ARDOISE = new Color(0x2C, 0x4A, 0x42);

    /** Produit le PDF du rapport de visite. */
    public byte[] generer(VisiteReponse v) {
        Document doc = new Document(PageSize.A4, 48, 48, 54, 48);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, sortie);
        doc.open();

        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, VERT_ARDOISE);
        Font soustitre = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, MIEL.darker());

        Paragraph entete = new Paragraph("Zümm — Rapport de visite", titre);
        doc.add(entete);
        doc.add(new Paragraph("Ruche %s (#%d) — visite du %s".formatted(
                v.rucheModele(), v.rucheId(), v.dateVisite()), soustitre));
        doc.add(espace());

        doc.add(section("Informations", sectionFont));
        doc.add(fiche(v));
        doc.add(espace());

        doc.add(section("Évaluations", sectionFont));
        doc.add(evaluations(v));
        doc.add(espace());

        doc.add(section("Observations", sectionFont));
        ajouterTexte(doc, "Constatations", v.constatations());
        ajouterTexte(doc, "Actions prévues", v.actionsPrevues());
        ajouterTexte(doc, "Actions effectuées", v.actionsEffectuees());
        ajouterTexte(doc, "Recommandations", v.recommandations());

        if (v.photos() != null && !v.photos().isEmpty()) {
            doc.add(espace());
            doc.add(section("Photos d'inspection", sectionFont));
            for (PhotoReponse p : v.photos()) {
                doc.add(new Paragraph("• " + p.url()
                        + (p.legende() == null ? "" : " — " + p.legende()), corps()));
            }
        }

        Paragraph pied = new Paragraph(
                "Document généré le " + LocalDate.now() + " par la console Zümm.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
        pied.setSpacingBefore(24);
        doc.add(pied);

        doc.close();
        return sortie.toByteArray();
    }

    private PdfPTable fiche(VisiteReponse v) {
        PdfPTable table = deuxColonnes();
        ligne(table, "Agent", v.agentNom());
        ligne(table, "Date", String.valueOf(v.dateVisite()));
        ligne(table, "Heure", v.heureVisite() == null ? "—" : v.heureVisite().toString());
        ligne(table, "Durée", v.dureeMin() == null ? "—" : v.dureeMin() + " min");
        ligne(table, "Raison", String.valueOf(v.raison()));
        return table;
    }

    private PdfPTable evaluations(VisiteReponse v) {
        PdfPTable table = deuxColonnes();
        ligne(table, "État sanitaire", v.etatSante() == null ? "—" : v.etatSante().toString());
        ligne(table, "Effectif", v.effectifQualitatif() == null ? "—" : v.effectifQualitatif().toString());
        ligne(table, "Productivité", v.productivite() == null ? "—" : v.productivite() + " / 3");
        return table;
    }

    private PdfPTable deuxColonnes() {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new int[] {1, 3});
        return table;
    }

    private void ligne(PdfPTable table, String cle, String valeur) {
        Font fcle = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, VERT_ARDOISE);
        PdfPCell cCle = new PdfPCell(new Phrase(cle, fcle));
        PdfPCell cVal = new PdfPCell(new Phrase(valeur == null ? "—" : valeur, corps()));
        for (PdfPCell c : new PdfPCell[] {cCle, cVal}) {
            c.setBorderColor(new Color(0xE5, 0xE5, 0xE5));
            c.setPadding(6);
        }
        table.addCell(cCle);
        table.addCell(cVal);
    }

    private void ajouterTexte(Document doc, String libelle, String texte) {
        Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, VERT_ARDOISE);
        Paragraph p = new Paragraph();
        p.add(new Phrase(libelle + " : ", gras));
        p.add(new Phrase((texte == null || texte.isBlank()) ? "—" : texte, corps()));
        p.setSpacingAfter(4);
        doc.add(p);
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
