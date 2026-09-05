package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.zumm.domain.EtatRuche;
import com.zumm.web.dto.GabaritReponse;
import com.zumm.web.dto.PointReferentiel;
import com.zumm.web.dto.RucheReponse;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fiche d'inspection vierge (SPRINT-24, gabarits au SPRINT-28 ; lot 2 du plan de
 * couverture).
 *
 * <p>Classe à 80,7 % d'instructions mais <strong>50 % de branches</strong> :
 * les quatre bascules du noyau et le plafond de colonnes n'étaient exercés dans
 * aucun sens. Or ce document est celui qu'on emporte au rucher, où l'on ne peut
 * rien corriger — une colonne manquante s'y note dans la marge, ou se perd.
 */
class FicheInspectionPdfServiceTest {

    private final FicheInspectionPdfService service = new FicheInspectionPdfService();

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

    private static RucheReponse ruche(long id, String modele) {
        return new RucheReponse(id, modele, 1L, "Rucher du haut", 2L, "Ferme des tilleuls",
                null, null, EtatRuche.ACTIVE, 1, List.of(), "dadant", "bleu", "achat",
                null, "normale", Instant.now(), Instant.now());
    }

    private static GabaritReponse gabarit(boolean couvain, boolean reine, boolean cadres,
            boolean temperament, List<String> points) {
        return new GabaritReponse(1L, "Suivi léger", "Trois points", couvain, reine, cadres,
                temperament, true, true, points, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("sans gabarit : la grille complète, chaque ruche nommée")
    void grilleComplete() throws Exception {
        String texte = texteDe(service.generer("Rucher du haut",
                List.of(ruche(41L, "Dadant"), ruche(42L, "Warré"))));

        assertThat(texte).contains("Fiche d'inspection");
        assertThat(texte).contains("Rucher Rucher du haut").contains("2 ruche(s)");
        // Une fiche où l'apiculteur doit recopier lui-même quarante
        // identifiants ne se remplit pas : la ruche est nommée.
        assertThat(texte).contains("#41 Dadant").contains("#42 Warré");
        assertThat(texte).contains("Œufs").contains("Couvain").contains("Réserves")
                .contains("Tempér.").contains("Notes");
        // La legende explique les colonnes imprimees, et seulement elles.
        assertThat(texte).contains("Œufs : cocher").contains("Tempérament :");
        assertThat(texte).contains("Agent :").contains("date :");
        assertThat(texte).contains("à ressaisir dans la console au retour");
    }

    @Test
    @DisplayName("un gabarit qui éteint tout le noyau ne garde que Ruche et Notes")
    void gabaritSansNoyau() throws Exception {
        String texte = texteDe(service.generer("Rucher du bas", List.of(ruche(1L, "Dadant")),
                gabarit(false, false, false, false, List.of()), List.of()));

        assertThat(texte).contains("Notes");
        // Masquer n'est pas effacer : les colonnes éteintes ne s'impriment pas,
        // et le reste de la fiche tient quand même. La legende disparait avec
        // elles — sinon la fiche expliquerait comment remplir des cases
        // absentes, ce qui se lit debout comme un oubli d'impression.
        assertThat(texte).doesNotContain("Œufs").doesNotContain("Cell. roy.")
                .doesNotContain("Réserves").doesNotContain("Tempér.");
    }

    @Test
    @DisplayName("chaque bascule du noyau allume ses colonnes, et elles seules")
    void basculesDuNoyau() throws Exception {
        String couvainSeul = texteDe(service.generer("R", List.of(ruche(1L, "D")),
                gabarit(true, false, false, false, List.of()), List.of()));
        String reineSeule = texteDe(service.generer("R", List.of(ruche(1L, "D")),
                gabarit(false, true, false, false, List.of()), List.of()));
        String cadresSeuls = texteDe(service.generer("R", List.of(ruche(1L, "D")),
                gabarit(false, false, true, false, List.of()), List.of()));
        String temperamentSeul = texteDe(service.generer("R", List.of(ruche(1L, "D")),
                gabarit(false, false, false, true, List.of()), List.of()));

        assertThat(couvainSeul).contains("Œufs").contains("Couvain").doesNotContain("Reine vue");
        assertThat(reineSeule).contains("Cell. roy.").contains("Reine vue")
                .doesNotContain("Œufs");
        assertThat(cadresSeuls).contains("Réserves").doesNotContain("Tempér.");
        assertThat(temperamentSeul).contains("Tempér.").doesNotContain("Réserves");
        // Et la legende suit, groupe par groupe.
        assertThat(couvainSeul).contains("Couvain : compter les cadres");
        assertThat(reineSeule).contains("Cellules royales : nombre");
    }

    @Test
    @DisplayName("un point du gabarit s'imprime avec son libellé français, pas son code")
    void libelleDepuisLeReferentiel() throws Exception {
        String texte = texteDe(service.generer("Rucher du haut", List.of(ruche(1L, "Dadant")),
                gabarit(false, false, false, false, List.of("varroa_visible")),
                List.of(new PointReferentiel("varroa_visible", "sanitaire", "booleen",
                        "Varroa visible", 10))));

        // Le document est imprimé par le serveur, qui n'a pas la langue du
        // navigateur : le libellé vient du référentiel, en français.
        assertThat(texte).contains("Varroa visible").doesNotContain("varroa_visible");
    }

    @Test
    @DisplayName("un code absent du référentiel s'imprime tel quel, plutôt que de disparaître")
    void codeInconnuImprimeTelQuel() throws Exception {
        String texte = texteDe(service.generer("R", List.of(ruche(1L, "D")),
                gabarit(false, false, false, false, List.of("point_orphelin")), List.of()));

        // Une colonne muette serait pire qu'une colonne au libellé technique :
        // l'apiculteur verrait une case sans savoir ce qu'on lui demande.
        assertThat(texte).contains("point_orphelin");
    }

    @Test
    @DisplayName("au-delà de cinq points, la fiche dit combien elle n'a pas imprimés")
    void plafondDeColonnesAnnonce() throws Exception {
        List<String> huit = IntStream.rangeClosed(1, 8).mapToObj(i -> "p" + i).toList();

        String texte = texteDe(service.generer("R", List.of(ruche(1L, "D")),
                gabarit(false, false, false, false, huit), List.of()));

        // La limite est physique — une page A4 paysage n'a pas de place. Ce qui
        // ne l'est pas, c'est de la taire : sans cette ligne, l'apiculteur
        // croirait avoir tout coché.
        assertThat(texte).contains("3 point(s) du gabarit non imprimé(s)");
        assertThat(texte).contains("p1").contains("p5").doesNotContain("p6");
    }

    @Test
    @DisplayName("exactement cinq points : rien n'est annoncé comme manquant")
    void plafondExact() throws Exception {
        List<String> cinq = IntStream.rangeClosed(1, 5).mapToObj(i -> "p" + i).toList();

        String texte = texteDe(service.generer("R", List.of(ruche(1L, "D")),
                gabarit(false, false, false, false, cinq), List.of()));

        assertThat(texte).contains("p5").doesNotContain("non imprimé(s)");
    }

    @Test
    @DisplayName("un rucher sans ruche connue produit quand même une fiche")
    void rucherSansRuche() throws Exception {
        String texte = texteDe(service.generer("Rucher neuf", List.of()));

        // La ligne vide supplémentaire est tout l'intérêt : au rucher, on trouve
        // toujours une ruche qui n'est pas encore au fichier.
        assertThat(texte).contains("Rucher Rucher neuf").contains("0 ruche(s)")
                .contains("Notes");
    }
}
