package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.zumm.web.dto.BilanExploitation;
import com.zumm.web.dto.ComparaisonSaisons;
import com.zumm.web.dto.ComparaisonSaisons.ProduitSaison;
import com.zumm.web.dto.RentabiliteRuche;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bilan annuel en PDF (SPRINT-27, lot 2 du plan de couverture).
 *
 * <p>Classe à 79,3 % d'instructions mais <strong>40 % de branches</strong> :
 * l'écart dit ce qui manquait — les cas où une donnée est absente. Or ce sont
 * exactement les cas où un bilan devient trompeur.
 *
 * <p><strong>Deux affirmations de ce document ne sont pas décoratives</strong>,
 * et ce sont elles que ces tests tiennent :
 *
 * <ul>
 *   <li>les recettes sont une <em>valorisation</em> au prix de
 *       {@code ConfigZumm.ini}, pas un chiffre d'affaires — Zümm ne connaît
 *       aucun prix de vente, et un PDF qui laisserait croire le contraire
 *       circulerait chez un comptable ;</li>
 *   <li>les dépenses non affectées ne sont <em>pas réparties</em> — une clé de
 *       répartition inventée donnerait une rentabilité par ruche plus jolie et
 *       moins vraie.</li>
 * </ul>
 */
class BilanAnnuelPdfServiceTest {

    private final BilanAnnuelPdfService service = new BilanAnnuelPdfService();

    private static String texteDe(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
        PdfReader lecteur = new PdfReader(pdf);
        try {
            PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
                sb.append(extracteur.getTextFromPage(page)).append('\n');
            }
            return sb.toString();
        } finally {
            lecteur.close();
        }
    }

    private static BilanExploitation bilan(Map<String, BigDecimal> postes,
            List<RentabiliteRuche> parRuche) {
        return new BilanExploitation(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("184.50"), new BigDecimal("1476.00"), new BigDecimal("930.25"),
                new BigDecimal("545.75"), new BigDecimal("310.00"), postes, parRuche);
    }

    private static Map<String, BigDecimal> postes() {
        Map<String, BigDecimal> postes = new LinkedHashMap<>();
        postes.put("consommable", new BigDecimal("420.25"));
        postes.put("assurance", new BigDecimal("310.00"));
        postes.put("materiel", new BigDecimal("200.00"));
        return postes;
    }

    @Test
    @DisplayName("porte les quatre sections, la période et l'année")
    void structureComplete() throws Exception {
        ComparaisonSaisons saison = new ComparaisonSaisons(2026, new BigDecimal("184.50"), 12,
                new BigDecimal("15.375"), 7, List.of());

        String texte = texteDe(service.generer(2026, bilan(postes(), List.of()), saison));

        assertThat(texte).contains("Bilan de la saison 2026");
        assertThat(texte).contains("2026-01-01").contains("2026-12-31");
        assertThat(texte).contains("Production").contains("Dépenses par poste")
                .contains("Résultat").contains("Rentabilité par ruche");
        assertThat(texte).contains("184.50 kg").contains("15.375 kg");
        assertThat(texte).contains("consommable").contains("420.25");
    }

    @Test
    @DisplayName("dit que les recettes sont une valorisation, pas un chiffre d'affaires")
    void avertissementSurLesRecettes() throws Exception {
        String texte = texteDe(service.generer(2026, bilan(postes(), List.of()), null));

        // Ce PDF circule sans la page qui l'a produit. S'il ne portait pas
        // cette phrase, « recettes 1476 € » se lirait comme du réalisé.
        assertThat(texte).contains("VALORISATION").contains("ConfigZumm.ini");
        assertThat(texte).contains("ne connaît pas les prix de vente");
        assertThat(texte).contains("Recettes (valorisation)");
    }

    @Test
    @DisplayName("les dépenses non affectées sont montrées à part, jamais réparties")
    void depensesNonAffecteesAPart() throws Exception {
        List<RentabiliteRuche> parRuche = List.of(
                new RentabiliteRuche(1L, "Dadant 1", "Rucher du haut", new BigDecimal("22.0"),
                        new BigDecimal("176.00"), new BigDecimal("60.00"), new BigDecimal("116.00")),
                new RentabiliteRuche(2L, "Dadant 2", "Rucher du haut", new BigDecimal("18.5"),
                        new BigDecimal("148.00"), new BigDecimal("60.00"), new BigDecimal("88.00")));

        String texte = texteDe(service.generer(2026, bilan(postes(), parRuche), null));

        assertThat(texte).contains("dont non affectées").contains("310.00");
        // « Une assurance ne se divise pas par le nombre de colonies » : la
        // phrase est dans le document, et c'est elle qui empêche un lecteur de
        // refaire la division lui-même en croyant corriger un oubli.
        assertThat(texte).contains("ne sont pas réparties");
        assertThat(texte).contains("Dadant 1").contains("Dadant 2");
    }

    @Test
    @DisplayName("un rendement non évaluable l'écrit, au lieu d'afficher zéro")
    void rendementNonEvalue() throws Exception {
        ComparaisonSaisons saison =
                new ComparaisonSaisons(2026, BigDecimal.ZERO, 0, null, 0, List.of());

        String texte = texteDe(service.generer(2026, bilan(postes(), List.of()), saison));

        // « 0 kg » ferait croire à une saison catastrophique là où il n'y a
        // simplement rien eu de saisi. Les deux se corrigent différemment.
        assertThat(texte).contains("non évalué").doesNotContain("null");
    }

    @Test
    @DisplayName("les autres produits sont listés, le miel n'est pas compté deux fois")
    void autresProduitsSansDoublonDuMiel() throws Exception {
        ComparaisonSaisons saison = new ComparaisonSaisons(2026, new BigDecimal("184.50"), 12,
                new BigDecimal("15.375"), 7, List.of(
                        new ProduitSaison("miel", "kg", new BigDecimal("184.50")),
                        new ProduitSaison("pollen", "kg", new BigDecimal("6.20")),
                        new ProduitSaison("essaim", "unite", new BigDecimal("4"))));

        String texte = texteDe(service.generer(2026, bilan(postes(), List.of()), saison));

        assertThat(texte).contains("Autre production — pollen").contains("6.20 kg");
        assertThat(texte).contains("Autre production — essaim").contains("4 unite");
        // Le miel a déjà sa ligne « Miel récolté » : le reprendre en « autre
        // production » le ferait lire deux fois dans le même tableau.
        assertThat(texte).doesNotContain("Autre production — miel");
    }

    @Test
    @DisplayName("sans comparaison de saison, le bilan sort quand même")
    void sansComparaisonDeSaison() throws Exception {
        String texte = texteDe(service.generer(2026, bilan(postes(), List.of()), null));

        assertThat(texte).contains("Miel récolté");
        assertThat(texte).doesNotContain("Ruches productives");
    }

    @Test
    @DisplayName("aucune dépense : la ligne le dit, au lieu d'un tableau vide")
    void aucuneDepense() throws Exception {
        String texte = texteDe(service.generer(2026, bilan(Map.of(), List.of()), null));

        assertThat(texte).contains("Aucune dépense enregistrée");
    }

    @Test
    @DisplayName("une ruche sans rucher affiche un tiret, jamais « null »")
    void rucheSansRucher() throws Exception {
        List<RentabiliteRuche> parRuche = List.of(
                new RentabiliteRuche(3L, "Warré 1", null, new BigDecimal("9.0"),
                        new BigDecimal("72.00"), new BigDecimal("40.00"), new BigDecimal("32.00")));

        String texte = texteDe(service.generer(2026, bilan(postes(), parRuche), null));

        assertThat(texte).contains("Warré 1").contains("—").doesNotContain("null");
    }
}
