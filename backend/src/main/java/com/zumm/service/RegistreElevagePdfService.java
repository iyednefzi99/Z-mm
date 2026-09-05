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
import com.zumm.web.dto.DossierConformite;
import com.zumm.web.dto.NourrissementReponse;
import com.zumm.web.dto.TraitementReponse;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Registre d'élevage réglementaire et dossier de contrôle (SPRINT-29, lot D).
 *
 * <p>Ferme deux lignes du §7 : « registre d'élevage réglementaire (PDF/Excel) »,
 * que <strong>cinq concurrents génèrent automatiquement</strong>, et « rapports
 * de conformité ». La dépendance de la première était levée depuis le
 * SPRINT-20 : traitements et nourrissements sont structurés, il ne manquait
 * qu'un service d'édition.
 *
 * <p><strong>Ce document n'invente aucune ligne.</strong> Il met en page ce que
 * le registre contient, et rien d'autre. Un registre d'élevage est opposable :
 * y ajouter une ligne « estimée » ou combler un trou par une moyenne le
 * rendrait faux là où il n'était qu'incomplet — le même raisonnement qu'au
 * SPRINT-22 sur le forçage de carence.
 *
 * <p>Le dossier de conformité suit le même principe, en plus strict : il
 * imprime son avertissement en tête, et jamais le mot « conforme » seul.
 */
@Service
public class RegistreElevagePdfService {

    private static final Color VERT_ARDOISE = new Color(0x2C, 0x4A, 0x42);
    private static final Color TRAIT = new Color(0xE5, 0xE5, 0xE5);
    private static final Color ALERTE = new Color(0xC0, 0x39, 0x2B);

    /**
     * Registre d'élevage d'une période.
     *
     * <p>Trois tableaux, dans l'ordre où un contrôle les demande : ce qui a été
     * administré, ce qui a été donné, et l'état sanitaire relevé.
     */
    public byte[] registre(LocalDate debut, LocalDate fin, List<TraitementReponse> traitements,
            List<NourrissementReponse> nourrissements) {
        Document doc = new Document(PageSize.A4.rotate(), 40, 40, 46, 40);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, sortie);
        doc.open();

        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, VERT_ARDOISE);
        Font petit = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

        doc.add(new Paragraph("Zümm — Registre d'élevage", titre));
        doc.add(new Paragraph("Période du %s au %s".formatted(debut, fin), petit));
        doc.add(new Paragraph(
                "Détenteur : ____________________  ·  N° NAPI/SIRET : ____________________",
                petit));
        doc.add(espace());

        doc.add(sousTitre("Traitements administrés"));
        doc.add(tableauTraitements(traitements));
        doc.add(espace());

        doc.add(sousTitre("Nourrissements"));
        doc.add(tableauNourrissements(nourrissements));

        doc.add(pied("Registre produit le " + LocalDate.now()
                + " à partir des saisies de la période. Aucune ligne n'y est estimée : "
                + "un registre incomplet se complète, il ne se comble pas."));
        doc.close();
        return sortie.toByteArray();
    }

    /** Dossier de contrôle : les points vérifiables, et tout ce qui ne l'est pas. */
    public byte[] dossier(DossierConformite dossier) {
        Document doc = new Document(PageSize.A4, 48, 48, 54, 48);
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, sortie);
        doc.open();

        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, VERT_ARDOISE);
        Font petit = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font avertissement = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, ALERTE);

        doc.add(new Paragraph("Zümm — Dossier de contrôle", titre));
        doc.add(new Paragraph("Période du %s au %s"
                .formatted(dossier.debut(), dossier.fin()), petit));
        doc.add(espace());
        // L'avertissement en TETE, et en rouge : un PDF circule sans la page qui
        // l'a produit, et c'est la premiere ligne qu'on lit.
        doc.add(new Paragraph(dossier.avertissement(), avertissement));
        doc.add(espace());

        PdfPTable table = new PdfPTable(new float[] {18, 16, 52, 14});
        table.setWidthPercentage(100);
        entete(table, List.of("Point", "État", "Ce que dit le système", "Éléments"));
        Font corps = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        for (DossierConformite.PointControle point : dossier.points()) {
            cellule(table, point.code(), corps);
            cellule(table, libelleStatut(point.statut()), corps);
            cellule(table, point.detail(), corps);
            cellule(table, String.valueOf(point.nombre()), corps);
        }
        doc.add(table);

        doc.add(pied("Produit le " + LocalDate.now()
                + ". « À justifier » ne veut pas dire « non conforme » : cela veut dire que "
                + "la pièce se trouve ailleurs que dans ce logiciel."));
        doc.close();
        return sortie.toByteArray();
    }

    private PdfPTable tableauTraitements(List<TraitementReponse> lignes) {
        PdfPTable table = new PdfPTable(new float[] {9, 14, 16, 14, 10, 12, 13, 12});
        table.setWidthPercentage(100);
        entete(table, List.of("Date", "Ruche", "Produit", "Substance", "Dose", "Fin",
                "Retrait", "Ordonnance"));
        Font corps = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        if (lignes.isEmpty()) {
            vide(table, 8, corps);
            return table;
        }
        for (TraitementReponse t : lignes) {
            cellule(table, String.valueOf(t.dateDebut()), corps);
            cellule(table, "#" + t.rucheId() + " " + t.rucheModele(), corps);
            cellule(table, t.produit(), corps);
            cellule(table, ouTiret(t.substanceActive()), corps);
            cellule(table, t.dose() == null ? "—"
                    : t.dose() + " " + ouTiret(t.doseUnite()), corps);
            cellule(table, t.dateFin() == null ? "en cours" : String.valueOf(t.dateFin()), corps);
            cellule(table, t.dateRetrait() == null ? "—" : String.valueOf(t.dateRetrait()), corps);
            cellule(table, ordonnance(t), corps);
        }
        return table;
    }

    private PdfPTable tableauNourrissements(List<NourrissementReponse> lignes) {
        PdfPTable table = new PdfPTable(new float[] {12, 20, 24, 20, 24});
        table.setWidthPercentage(100);
        entete(table, List.of("Date", "Ruche", "Type", "Quantité", "Motif"));
        Font corps = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
        if (lignes.isEmpty()) {
            vide(table, 5, corps);
            return table;
        }
        for (NourrissementReponse n : lignes) {
            cellule(table, String.valueOf(n.dateApport()), corps);
            cellule(table, "#" + n.rucheId() + " " + n.rucheModele(), corps);
            cellule(table, n.typeAliment(), corps);
            cellule(table, n.quantite() + " " + ouTiret(n.quantiteUnite()), corps);
            cellule(table, ouTiret(n.motif()), corps);
        }
        return table;
    }

    private static String ordonnance(TraitementReponse t) {
        if (t.ordonnance() == null) {
            return "—";
        }
        return t.ordonnanceVeterinaire() == null
                ? t.ordonnance()
                : t.ordonnance() + " (" + t.ordonnanceVeterinaire() + ")";
    }

    private static String libelleStatut(String statut) {
        return switch (statut) {
            case "verifie" -> "Vérifié";
            case "signale" -> "Signalé";
            default -> "À justifier";
        };
    }

    private void entete(PdfPTable table, List<String> colonnes) {
        Font police = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        for (String colonne : colonnes) {
            PdfPCell cellule = new PdfPCell(new Phrase(colonne, police));
            cellule.setBackgroundColor(VERT_ARDOISE);
            cellule.setPadding(5);
            cellule.setBorderColor(VERT_ARDOISE);
            table.addCell(cellule);
        }
    }

    private void cellule(PdfPTable table, String valeur, Font corps) {
        PdfPCell cellule = new PdfPCell(new Phrase(valeur == null ? "—" : valeur, corps));
        cellule.setPadding(5);
        cellule.setBorderColor(TRAIT);
        table.addCell(cellule);
    }

    /**
     * Une ligne « aucune saisie », et non un tableau vide.
     *
     * <p>Un tableau sans corps se lit comme une page mal imprimée ; cette ligne
     * dit que le registre a bien été consulté et qu'il ne contenait rien.
     */
    private void vide(PdfPTable table, int colonnes, Font corps) {
        PdfPCell cellule = new PdfPCell(new Phrase("Aucune saisie sur la période.", corps));
        cellule.setColspan(colonnes);
        cellule.setPadding(7);
        cellule.setBorderColor(TRAIT);
        table.addCell(cellule);
    }

    private static String ouTiret(String valeur) {
        return valeur == null || valeur.isBlank() ? "—" : valeur;
    }

    private Paragraph sousTitre(String texte) {
        Paragraph p = new Paragraph(texte,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, VERT_ARDOISE));
        p.setSpacingBefore(8);
        p.setSpacingAfter(5);
        return p;
    }

    private Paragraph pied(String texte) {
        Paragraph p = new Paragraph(texte,
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
        p.setSpacingBefore(12);
        return p;
    }

    private Paragraph espace() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(2);
        return p;
    }
}
