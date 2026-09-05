package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import com.zumm.domain.EffectifQualitatif;
import com.zumm.domain.EtatSante;
import com.zumm.domain.RaisonVisite;
import com.zumm.web.dto.PhotoReponse;
import com.zumm.web.dto.VisiteReponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rendu PDF du rapport de visite (US-044, SPRINT-09 ; complété au lot 2 du plan
 * de couverture).
 *
 * <p>La classe était à 61,5 % de branches : toutes ses branches sont des champs
 * ABSENTS, et aucune n'était exercée. Un rapport de visite est pourtant, la
 * plupart du temps, à moitié rempli — on note ce qu'on a vu, pas ce qu'on aurait
 * pu voir. Le cas partiel est donc le cas normal.
 *
 * <p>Ces tests lisent le texte du PDF plutôt que sa seule signature : un test
 * qui vérifie que les octets commencent par {@code %PDF} resterait vert si
 * toutes les cellules sortaient vides.
 */
class RapportVisitePdfServiceTest {

    private final RapportVisitePdfService service = new RapportVisitePdfService();

    private VisiteReponse visite(List<PhotoReponse> photos) {
        return new VisiteReponse(1L, 2L, "Langstroth", 3L, "Amine Trabelsi", null,
                LocalDate.of(2026, 6, 15), LocalTime.of(9, 30), 25, RaisonVisite.CONTROLE,
                "Colonie vigoureuse, couvain compact.", "Poser une hausse", "Hausse posée",
                "Surveiller les réserves", EffectifQualitatif.FORT, EtatSante.BON, 3,
                // Grille d'inspection, météo figée et pathologies (SPRINT-20),
                // points du carnet paramétrable (SPRINT-28) : laissés vides ici,
                // le rapport PDF doit sortir sans eux.
                null, null, List.of(), List.of(),
                photos, Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("produit un PDF non vide (en-tête %PDF)")
    void produitUnPdf() {
        byte[] pdf = service.generer(visite(List.of()));

        assertThat(pdf).isNotEmpty();
        // Signature d'un fichier PDF : les octets « %PDF ».
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("inclut les photos, avec et sans légende")
    void inclutLesPhotos() throws Exception {
        var avecLegende = new PhotoReponse(9L, com.zumm.domain.Photo.Cible.VISITE, 4L,
                "https://demo.zumm.tn/p.jpg", "Cadre de couvain", Instant.now());
        var sansLegende = new PhotoReponse(10L, com.zumm.domain.Photo.Cible.VISITE, 4L,
                "https://demo.zumm.tn/q.jpg", null, Instant.now());

        String texte = texteDe(service.generer(visite(List.of(avecLegende, sansLegende))));

        assertThat(texte).contains("Cadre de couvain");
        // Une photo sans légende ne doit pas traîner un tiret cadratin orphelin
        // derrière son adresse : le séparateur n'existe que s'il sépare.
        assertThat(texte).contains("q.jpg").doesNotContain("q.jpg —");
    }

    @Test
    @DisplayName("porte les champs renseignés, et l'agent qui a fait la visite")
    void champsRenseignes() throws Exception {
        String texte = texteDe(service.generer(visite(List.of())));

        assertThat(texte).contains("Amine Trabelsi").contains("Langstroth");
        assertThat(texte).contains("2026-06-15").contains("09:30").contains("25 min");
        assertThat(texte).contains("Colonie vigoureuse");
        assertThat(texte).contains("Hausse posée").contains("Surveiller les réserves");
    }

    @Test
    @DisplayName("un rapport à moitié rempli sort en tirets, jamais en « null »")
    void rapportPartiel() throws Exception {
        // Le cas NORMAL : on note ce qu'on a vu. Sans ces tirets, le rapport
        // afficherait « null » là où l'agent n'a rien constaté — ce qui se lit
        // comme une donnée, et non comme une absence de donnée.
        VisiteReponse partielle = new VisiteReponse(1L, 2L, "Langstroth", 3L, "Amine Trabelsi",
                null, LocalDate.of(2026, 6, 15), null, null, RaisonVisite.CONTROLE,
                null, null, null, null, null, null, null,
                null, null, List.of(), List.of(),
                List.of(), Instant.now(), Instant.now());

        String texte = texteDe(service.generer(partielle));

        assertThat(texte).doesNotContain("null");
        assertThat(texte).contains("—");
    }

    /** Le texte rendu, page par page, comme un lecteur du PDF le verrait. */
    private static String texteDe(byte[] pdf) throws Exception {
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF");
        PdfReader lecteur = new PdfReader(pdf);
        try {
            PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
                sb.append(extracteur.getTextFromPage(page)).append(System.lineSeparator());
            }
            return sb.toString();
        } finally {
            lecteur.close();
        }
    }
}
