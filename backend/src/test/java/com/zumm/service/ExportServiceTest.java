package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.Consommable;
import com.zumm.domain.ComptageVarroa;
import com.zumm.domain.Depense;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ferme;
import com.zumm.domain.LotConditionnement;
import com.zumm.domain.Materiel;
import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.Nourrissement;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.Recolte;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.Tache;
import com.zumm.domain.Traitement;
import com.zumm.domain.TypeIndicateur;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.ComptageVarroaRepository;
import com.zumm.repository.ConsommableRepository;
import com.zumm.repository.DepenseRepository;
import com.zumm.repository.LotConditionnementRepository;
import com.zumm.repository.MaterielRepository;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.NourrissementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.service.ExportService.Format;
import com.zumm.web.RequeteInvalide;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Export tabulaire (US-027, US-036, lot 2 du plan de couverture).
 *
 * <p><strong>Pourquoi cette classe etait a 48,4 %.</strong> Quatorze ressources
 * et trois formats font quarante-deux sorties ; deux etaient testees. Le reste
 * est pourtant une fonction PURE — une grille de chaines en entree, des octets
 * en sortie — qui ne demande ni base ni conteneur, seulement quatorze depots
 * simules rendant une ligne chacun.
 *
 * <p>Trois proprietes valent bien au-dela du pourcentage :
 *
 * <ol>
 *   <li><strong>La neutralisation des formules</strong> ({@code CWE-1236}). Un
 *       champ de note commencant par {@code =cmd|...} devient une commande
 *       executee a l'ouverture du fichier. Le refactor du SPRINT-27 a rassemble
 *       cette garde en un seul endroit precisement pour qu'elle ne disparaisse
 *       pas ; encore faut-il qu'un test la tienne.</li>
 *   <li><strong>Ce qui ne sort PAS.</strong> Aucune position de rucher, aucune
 *       adresse e-mail, aucun taux de varroa. Ce sont trois decisions ecrites
 *       dans le code, et un export est un fichier qui circule : la seule facon
 *       de savoir qu'une colonne n'est pas revenue est de le verifier.</li>
 *   <li><strong>La borne de quatre-vingt-dix jours sur les mesures.</strong> Une
 *       hypertable de capteurs compte des millions de lignes ; sans borne,
 *       l'export les charge toutes en memoire.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock private VisiteRepository visites;
    @Mock private RucheRepository ruches;
    @Mock private SiteRepository sites;
    @Mock private AgentRepository agents;
    @Mock private RecolteRepository recoltes;
    @Mock private TacheRepository taches;
    @Mock private TraitementRepository traitements;
    @Mock private NourrissementRepository nourrissements;
    @Mock private LotConditionnementRepository lots;
    @Mock private MesureRepository mesures;
    @Mock private DepenseRepository depenses;
    @Mock private MaterielRepository materiels;
    @Mock private ConsommableRepository consommables;
    @Mock private ComptageVarroaRepository comptages;

    private ExportService service;

    private static final Ferme FERME = new Ferme("Ferme des tilleuls", null);
    private static final Site SITE =
            new Site("Rucher du haut", FERME, new BigDecimal("36.8"), new BigDecimal("10.2"),
                    LocalDate.of(2026, 3, 1));
    private static final Agent AGENT = new Agent("Amal", RoleAgent.APICULTEUR, FERME);
    private static final Ruche RUCHE = new Ruche("Dadant", SITE, FERME, EtatRuche.ACTIVE);

    @BeforeEach
    void monter() {
        service = new ExportService(visites, ruches, sites, agents, recoltes, taches,
                traitements, nourrissements, lots, mesures, depenses, materiels,
                consommables, comptages);
    }

    /** Une ligne dans chaque depot : chaque grille a de quoi produire un corps. */
    private void unePourChaque() {
        Visite visite = new Visite(RUCHE, AGENT, LocalDate.of(2026, 9, 10), RaisonVisite.CONTROLE);
        visite.setConstatations("Colonie forte");
        Nourrissement nourrissement = new Nourrissement(RUCHE, AGENT,
                LocalDate.of(2026, 9, 1), "sirop 1:1", new BigDecimal("2.5"), "L");
        Mesure mesure = new Mesure(
                new MesureId(1L, TypeIndicateur.POIDS, Instant.now()), new BigDecimal("41.2"));

        lenient().when(visites.findAllByOrderByDateVisiteAsc()).thenReturn(List.of(visite));
        lenient().when(ruches.findAll()).thenReturn(List.of(RUCHE));
        lenient().when(sites.findAll()).thenReturn(List.of(SITE));
        lenient().when(agents.findAll()).thenReturn(List.of(AGENT));
        lenient().when(recoltes.findByOrderByDateRecolteDescIdDesc()).thenReturn(List.of(
                new Recolte(RUCHE, LocalDate.of(2026, 8, 20), new BigDecimal("18.4"), "L-2026-1")));
        lenient().when(taches.findAll()).thenReturn(List.of(new Tache("Poser une hausse")));
        lenient().when(traitements.findAll()).thenReturn(List.of(new Traitement(
                RUCHE, AGENT, "Apivar", "varroa", LocalDate.of(2026, 8, 1))));
        lenient().when(nourrissements.findAll()).thenReturn(List.of(nourrissement));
        lenient().when(lots.findAll()).thenReturn(List.of(new LotConditionnement(
                "L-2026-1", LocalDate.of(2026, 8, 25), new BigDecimal("18.0"))));
        lenient().when(mesures.findAll()).thenReturn(List.of(mesure));
        lenient().when(depenses.findAllByOrderByDateDepenseDescIdDesc()).thenReturn(List.of(
                new Depense("Cire", "consommable", new BigDecimal("120.00"),
                        LocalDate.of(2026, 7, 3))));
        lenient().when(materiels.findAllByOrderByCategorieAscLibelleAsc()).thenReturn(List.of(
                new Materiel("Enfumoir", "outil", 3)));
        lenient().when(consommables.findAllByOrderByCategorieAscLibelleAsc()).thenReturn(List.of(
                new Consommable("Candi", "nourrissement", "kg")));
        lenient().when(comptages.findAll()).thenReturn(List.of(new ComptageVarroa(
                RUCHE, AGENT, LocalDate.of(2026, 9, 2), "lange", 12)));
    }

    private String texte(String ressource, Format format) {
        return new String(service.exporter(ressource, format), StandardCharsets.UTF_8);
    }

    // ── Les quatorze ressources ─────────────────────────────────────────────

    static List<String> ressources() {
        return ExportService.ressources();
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("chaque ressource rend un en-tete et une ligne, en CSV")
    @MethodSource("ressources")
    void chaqueRessourceProduitUneGrille(String ressource) {
        unePourChaque();

        String csv = texte(ressource, Format.CSV);
        List<String> lignes = csv.lines().toList();

        // Deux lignes exactement : l'en-tete, et l'unique enregistrement. Une
        // grille qui rendrait un en-tete seul passerait une assertion « non
        // vide » sans rien exporter — c'est le defaut que ce test attrape.
        assertThat(lignes).hasSize(2);
        assertThat(lignes.get(0)).isNotBlank().doesNotContain("null");
        assertThat(lignes.get(1)).isNotBlank();
        assertThat(csv).endsWith("\r\n");
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("chaque ressource rend un XLSX ouvrable, en-tete compris")
    @MethodSource("ressources")
    void chaqueRessourceProduitUnXlsx(String ressource) {
        unePourChaque();

        byte[] classeur = service.exporter(ressource, Format.XLSX);

        // Signature d'un ZIP : un XLSX en est un. Le contenu est verifie par
        // ClasseurXlsxTest ; ici on tient le branchement de format.
        assertThat(classeur).hasSizeGreaterThan(200);
        assertThat(classeur[0]).isEqualTo((byte) 'P');
        assertThat(classeur[1]).isEqualTo((byte) 'K');
    }

    @Test
    @DisplayName("les quatorze ressources annoncees sont exactement celles qui s'exportent")
    void ressourcesAnnonceesEtServies() {
        unePourChaque();

        assertThat(ExportService.ressources()).hasSize(14);
        // L'ecran propose cette liste ; une entree qui ne s'exporterait pas
        // serait un bouton qui echoue en 400.
        for (String ressource : ExportService.ressources()) {
            assertThat(service.exporter(ressource, Format.CSV)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("une ressource inconnue est refusee, pas rendue vide")
    void ressourceInconnue() {
        assertThatThrownBy(() -> service.exporter("colonies", Format.CSV))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("colonies");
    }

    // ── Ce qui ne sort pas ──────────────────────────────────────────────────

    @Test
    @DisplayName("l'export des ruchers ne porte aucune coordonnee")
    void aucunePositionDansLExport() {
        unePourChaque();

        String csv = texte("sites", Format.CSV);

        // Un CSV n'a pas de role porteur : ce que PolitiquePositions protege
        // partout ailleurs se protege ici en n'ecrivant pas la colonne.
        assertThat(csv).doesNotContain("36.8").doesNotContain("10.2");
        assertThat(csv.lines().findFirst().orElseThrow())
                .doesNotContain("latitude").doesNotContain("longitude");
    }

    @Test
    @DisplayName("l'export des agents ne porte aucune adresse e-mail")
    void aucuneAdresseDansLExport() {
        AGENT.setEmail("amal@exemple.tn");
        unePourChaque();

        String csv = texte("agents", Format.CSV);

        // Lecon n° 1 du §10 du document d'ecart : des adresses ont circule.
        assertThat(csv).doesNotContain("amal@exemple.tn").doesNotContain("@exemple");
        // `notifications_email` est une PREFERENCE, pas une adresse : la colonne
        // a le droit d'exister. Ce qui ne doit pas exister, c'est une colonne
        // `email` — d'ou la comparaison exacte, colonne par colonne.
        assertThat(csv.lines().findFirst().orElseThrow().split(","))
                .doesNotContain("email", "courriel", "adresse");
    }

    @Test
    @DisplayName("l'export du varroa porte la methode et jamais le taux")
    void aucunTauxDeVarroa() {
        unePourChaque();

        String entete = texte("varroa", Format.CSV).lines().findFirst().orElseThrow();

        // Le taux n'a pas la meme unite selon la methode : une colonne « taux »
        // sans sa methode rendrait facile une comparaison fausse.
        assertThat(entete).contains("methode").doesNotContain("taux");
    }

    @Test
    @DisplayName("les mesures sont bornees a quatre-vingt-dix jours")
    void mesuresBornees() {
        Instant recent = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant ancien = Instant.now().minus(120, ChronoUnit.DAYS);
        when(mesures.findAll()).thenReturn(List.of(
                new Mesure(new MesureId(1L, TypeIndicateur.POIDS, ancien), new BigDecimal("40.0")),
                new Mesure(new MesureId(2L, TypeIndicateur.POIDS, recent), new BigDecimal("41.2"))));

        String csv = texte("mesures", Format.CSV);

        // Sans la borne, une hypertable de capteurs part entiere en memoire.
        assertThat(csv.lines()).hasSize(2);
        assertThat(csv).contains("41.2").doesNotContain("40.0");
    }

    // ── Formats ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("choix du format")
    class ChoixDuFormat {

        @ParameterizedTest(name = "\"{0}\" → {1}")
        @CsvSource({"csv, CSV", "CSV, CSV", "txt, TXT", "TXT, TXT", "xlsx, XLSX", "XLSX, XLSX"})
        @DisplayName("le libelle est lu sans tenir compte de la casse")
        void depuisLibelle(String valeur, Format attendu) {
            assertThat(Format.depuis(valeur)).isEqualTo(attendu);
        }

        @ParameterizedTest
        @ValueSource(strings = {"pdf", "ods", "", "  "})
        @DisplayName("un format inconnu retombe sur CSV plutot que d'echouer")
        void formatInconnu(String valeur) {
            // Un export est une commodite : refuser en 400 parce qu'on a tape
            // « ods » couterait plus a l'utilisateur que de lui rendre un CSV.
            assertThat(Format.depuis(valeur)).isEqualTo(Format.CSV);
        }

        @Test
        @DisplayName("format absent : CSV")
        void formatAbsent() {
            assertThat(Format.depuis(null)).isEqualTo(Format.CSV);
        }

        @Test
        @DisplayName("chaque format annonce son type MIME, son extension et sa nature")
        void metadonneesDeFormat() {
            assertThat(Format.CSV.typeMime()).isEqualTo("text/csv");
            assertThat(Format.CSV.extension()).isEqualTo("csv");
            assertThat(Format.CSV.estTexte()).isTrue();
            assertThat(Format.TXT.typeMime()).isEqualTo("text/plain");
            assertThat(Format.TXT.extension()).isEqualTo("txt");
            assertThat(Format.TXT.estTexte()).isTrue();
            assertThat(Format.XLSX.typeMime()).isEqualTo(ClasseurXlsx.TYPE_MIME);
            assertThat(Format.XLSX.extension()).isEqualTo("xlsx");
            // Le seul format binaire : c'est ce booleen qui aiguille le rendu.
            assertThat(Format.XLSX.estTexte()).isFalse();
        }
    }

    // ── Echappement ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("echappement")
    class Echappement {

        private String csvAvecConstatations(String constatations) {
            Visite visite =
                    new Visite(RUCHE, AGENT, LocalDate.of(2026, 9, 10), RaisonVisite.CONTROLE);
            visite.setConstatations(constatations);
            when(visites.findAllByOrderByDateVisiteAsc()).thenReturn(List.of(visite));
            return texte("visites", Format.CSV);
        }

        @Test
        @DisplayName("CSV : en-tete, et champ contenant une virgule entoure de guillemets")
        void csvEchappeLesVirgules() {
            assertThat(csvAvecConstatations("Colonie forte, calme"))
                    .startsWith("id,date,heure")
                    .contains("\"Colonie forte, calme\"");
        }

        @Test
        @DisplayName("CSV : un guillemet interne est double, RFC 4180")
        void csvDoubleLesGuillemets() {
            assertThat(csvAvecConstatations("reine dite \"jaune\""))
                    .contains("\"reine dite \"\"jaune\"\"\"");
        }

        @Test
        @DisplayName("CSV : un retour a la ligne interne est cite, pas supprime")
        void csvCiteLesRetoursLigne() {
            String csv = csvAvecConstatations("ligne un\nligne deux");

            // Cite, la cellule reste UNE cellule a la relecture ; supprime, la
            // note perdrait sa mise en forme sans qu'on le sache.
            assertThat(csv).contains("\"ligne un\nligne deux\"");
        }

        @ParameterizedTest(name = "TXT : {0} devient une espace")
        @ValueSource(strings = {"\t", "\r", "\n"})
        @DisplayName("TXT : tout ce qui deplacerait une colonne devient une espace")
        void txtNeutraliseLesSeparateurs(String cassure) {
            String txt;
            Visite visite =
                    new Visite(RUCHE, AGENT, LocalDate.of(2026, 9, 11), RaisonVisite.RECOLTE);
            visite.setConstatations("avant" + cassure + "apres");
            when(visites.findAllByOrderByDateVisiteAsc()).thenReturn(List.of(visite));
            txt = texte("visites", Format.TXT);

            assertThat(txt).contains("avant apres");
        }

        @Test
        @DisplayName("TXT : separateur tabulation")
        void txtSepareParTabulation() {
            Visite visite =
                    new Visite(RUCHE, AGENT, LocalDate.of(2026, 9, 11), RaisonVisite.RECOLTE);
            when(visites.findAllByOrderByDateVisiteAsc()).thenReturn(List.of(visite));

            String txt = texte("visites", Format.TXT);

            assertThat(txt).startsWith("id\tdate\theure").contains("Dadant\tAmal");
        }
    }

    // ── Injection de formule (CWE-1236) ─────────────────────────────────────

    @Nested
    @DisplayName("neutralisation des formules")
    class Formules {

        private String cellule(String contenu, Format format) {
            Visite visite =
                    new Visite(RUCHE, AGENT, LocalDate.of(2026, 9, 10), RaisonVisite.CONTROLE);
            visite.setConstatations(contenu);
            when(visites.findAllByOrderByDateVisiteAsc()).thenReturn(List.of(visite));
            return texte("visites", format);
        }

        @ParameterizedTest(name = "amorce {0}")
        @ValueSource(strings = {"=cmd|'/c calc'!A1", "+1+1", "@SUM(A1)", "-2+3+cmd"})
        @DisplayName("une amorce de formule est prefixee d'une apostrophe")
        void amorcesNeutralisees(String charge) {
            // Sans cette garde, ouvrir le fichier exporte suffit a executer la
            // charge : c'est une execution de commande a distance par un champ
            // de note.
            assertThat(cellule(charge, Format.CSV)).contains("'" + charge.charAt(0));
        }

        @ParameterizedTest(name = "nombre {0}")
        @ValueSource(strings = {"-12", "-3.5", "-0.001"})
        @DisplayName("un nombre negatif reste un nombre")
        void nombresNegatifsIntacts(String nombre) {
            String csv = cellule(nombre, Format.CSV);

            // Prefixer casserait tout export numerique — et un nombre negatif
            // ne peut rien executer.
            assertThat(csv).contains("," + nombre).doesNotContain("'" + nombre);
        }

        @Test
        @DisplayName("un champ vide n'est pas prefixe")
        void champVideIntact() {
            assertThat(cellule("", Format.CSV)).doesNotContain("'");
        }

        @Test
        @DisplayName("la garde vaut aussi pour le TXT, pas seulement pour le CSV")
        void gardeValableEnTxt() {
            // Le TXT s'ouvre dans le meme tableur. Une garde qui ne vaudrait que
            // pour le CSV serait une garde a moitie posee.
            assertThat(cellule("=1+1", Format.TXT)).contains("'=1+1");
        }
    }
}
