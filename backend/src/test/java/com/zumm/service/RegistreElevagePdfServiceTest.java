package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.zumm.web.dto.DossierConformite;
import com.zumm.web.dto.DossierConformite.PointControle;
import com.zumm.web.dto.NourrissementReponse;
import com.zumm.web.dto.TraitementReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Registre d'élevage et dossier de contrôle (SPRINT-29, lot 2 du plan de
 * couverture).
 *
 * <p><strong>Pourquoi cette classe était à 48,6 % d'instructions et 12,9 % de
 * branches</strong> — la pire couverture de branches du dépôt hors moteur de
 * règles. Aucun test ne l'appelait, et ses branches sont toutes des cas de
 * données manquantes : une dose absente, un traitement en cours, une ordonnance
 * sans vétérinaire, une période sans aucune saisie.
 *
 * <p><strong>Ces tests lisent le TEXTE du PDF</strong>, pas seulement sa
 * signature. Un test qui vérifie que les octets commencent par {@code %PDF}
 * exécute le code sans rien affirmer : il resterait vert si toutes les cellules
 * sortaient vides. Or ce document est un registre réglementaire présenté à un
 * contrôleur — ce qu'il contient est précisément ce qui compte.
 */
class RegistreElevagePdfServiceTest {

    private final RegistreElevagePdfService service = new RegistreElevagePdfService();

    /** Le texte rendu, page par page, comme un contrôleur le lirait. */
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

    private static TraitementReponse traitement(BigDecimal dose, String doseUnite,
            LocalDate dateFin, LocalDate dateRetrait, String ordonnance, String veterinaire) {
        return new TraitementReponse(1L, 42L, "Dadant", 3L, "Amal", null,
                "Apivar", "amitraze", "varroa", dose, doseUnite,
                LocalDate.of(2026, 8, 1), dateFin, 14, dateRetrait, false,
                ordonnance, veterinaire, LocalDate.of(2026, 7, 30), null,
                Instant.now(), Instant.now());
    }

    private static NourrissementReponse nourrissement(String unite, String motif) {
        return new NourrissementReponse(7L, 42L, "Dadant", 3L, "Amal", null,
                LocalDate.of(2026, 9, 1), "sirop 1:1", new BigDecimal("2.5"), unite, motif,
                null, Instant.now(), Instant.now());
    }

    @Nested
    @DisplayName("registre d'élevage")
    class Registre {

        @Test
        @DisplayName("porte l'en-tête réglementaire, la période et les deux tableaux")
        void enteteEtTableaux() throws Exception {
            byte[] pdf = service.registre(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    List.of(traitement(new BigDecimal("1.5"), "bandelettes",
                            LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 29),
                            "ORD-2026-04", "Dr Ben Ali")),
                    List.of(nourrissement("L", "préparation hivernale")));

            String texte = texteDe(pdf);

            // Un registre présenté à un contrôleur doit porter les champs qu'il
            // vient vérifier — à commencer par ceux qu'il devra remplir à la main.
            assertThat(texte).contains("Registre d'élevage");
            assertThat(texte).contains("2026-01-01").contains("2026-12-31");
            assertThat(texte).contains("NAPI/SIRET");
            assertThat(texte).contains("Traitements administrés").contains("Nourrissements");
            assertThat(texte).contains("Apivar").contains("amitraze");
            assertThat(texte).contains("#42 Dadant");
            assertThat(texte).contains("sirop 1:1");
        }

        @Test
        @DisplayName("un traitement en cours l'écrit, au lieu de laisser la case vide")
        void traitementEnCours() throws Exception {
            byte[] pdf = service.registre(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    List.of(traitement(new BigDecimal("1.5"), "bandelettes", null, null,
                            "ORD-2026-04", null)),
                    List.of());

            String texte = texteDe(pdf);

            // Une case vide se lit comme un oubli de saisie ; « en cours » dit
            // que le traitement court encore, ce qui n'est pas la même chose
            // devant un contrôleur.
            assertThat(texte).contains("en cours");
        }

        @Test
        @DisplayName("une ordonnance sans vétérinaire nommé sort sans parenthèse vide")
        void ordonnanceSansVeterinaire() throws Exception {
            byte[] avecVeto = service.registre(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                    List.of(traitement(new BigDecimal("1"), "bandelette",
                            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 15),
                            "ORD-1", "Dr Ben Ali")),
                    List.of());
            byte[] sansVeto = service.registre(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30),
                    List.of(traitement(new BigDecimal("1"), "bandelette",
                            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 15),
                            "ORD-1", null)),
                    List.of());

            assertThat(texteDe(avecVeto)).contains("ORD-1 (Dr Ben Ali)");
            assertThat(texteDe(sansVeto)).contains("ORD-1").doesNotContain("()");
        }

        @Test
        @DisplayName("une dose absente et un champ vide sortent en tiret, jamais en « null »")
        void donneesManquantes() throws Exception {
            byte[] pdf = service.registre(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    List.of(traitement(null, null, null, null, null, null)),
                    List.of(nourrissement(null, null)));

            String texte = texteDe(pdf);

            // « null » dans un registre réglementaire est pire qu'une case vide :
            // il donne à lire une valeur là où il n'y en a pas.
            assertThat(texte).doesNotContain("null");
            assertThat(texte).contains("—");
        }

        @Test
        @DisplayName("une période sans saisie l'écrit, au lieu d'un tableau sans corps")
        void periodeSansSaisie() throws Exception {
            byte[] pdf = service.registre(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), List.of(), List.of());

            String texte = texteDe(pdf);

            // Un tableau sans corps se lit comme une page mal imprimée ; cette
            // ligne dit que le registre a été consulté et qu'il était vide.
            assertThat(texte).contains("Aucune saisie sur la période.");
            assertThat(texte).contains("un registre incomplet se complète");
        }
    }

    @Nested
    @DisplayName("dossier de contrôle")
    class Dossier {

        private DossierConformite dossier(List<PointControle> points) {
            return new DossierConformite(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    "Zümm ne certifie rien : ce dossier rassemble des pièces.",
                    points);
        }

        @Test
        @DisplayName("les trois statuts sont traduits en clair")
        void troisStatuts() throws Exception {
            byte[] pdf = service.dossier(dossier(List.of(
                    new PointControle("sucres", "verifie", "Douze nourrissements tracés", 12),
                    new PointControle("cire", "signale", "Deux lots sans origine", 2),
                    new PointControle("foncier", "a_justifier", "Hors du logiciel", 0))));

            String texte = texteDe(pdf);

            assertThat(texte).contains("Vérifié").contains("Signalé").contains("À justifier");
            assertThat(texte).contains("Douze nourrissements tracés");
            assertThat(texte).contains("12").contains("0");
        }

        @Test
        @DisplayName("un statut inconnu retombe sur « À justifier », jamais sur son code brut")
        void statutInconnu() throws Exception {
            byte[] pdf = service.dossier(dossier(List.of(
                    new PointControle("divers", "etat-imprevu", "Point non classé", 1))));

            String texte = texteDe(pdf);

            // Le défaut prudent : un point qu'on ne sait pas classer ne doit pas
            // se lire comme vérifié, et son code interne n'a rien à faire sous
            // les yeux d'un contrôleur.
            assertThat(texte).contains("À justifier").doesNotContain("etat-imprevu");
        }

        @Test
        @DisplayName("l'avertissement est en tête, et le pied dit ce que « à justifier » signifie")
        void avertissementEnTete() throws Exception {
            byte[] pdf = service.dossier(dossier(List.of(
                    new PointControle("sucres", "verifie", "Tracés", 3))));

            String texte = texteDe(pdf);

            // Un PDF circule sans la page qui l'a produit : l'avertissement doit
            // voyager avec lui, et arriver avant le tableau.
            assertThat(texte).contains("Zümm ne certifie rien");
            assertThat(texte.indexOf("Zümm ne certifie rien"))
                    .isLessThan(texte.indexOf("Tracés"));
            assertThat(texte).contains("« À justifier » ne veut pas dire « non conforme »");
        }

        @Test
        @DisplayName("un dossier sans aucun point sort quand même, avec son avertissement")
        void dossierSansPoint() throws Exception {
            byte[] pdf = service.dossier(dossier(List.of()));

            assertThat(texteDe(pdf)).contains("Dossier de contrôle").contains("ne certifie rien");
        }
    }
}
