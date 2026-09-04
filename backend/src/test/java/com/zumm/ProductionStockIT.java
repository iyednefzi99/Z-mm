package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Éprouve le SPRINT-27, lot E — production, stock, matériel, comptabilité.
 *
 * <p>Le lot est large et mécanique ; ce qui mérite un test d'intégration est ce
 * qui ne se voit qu'en base ou qu'à la sortie :
 *
 * <ol>
 *   <li>un essaim se compte, il ne se pèse pas — et la base refuse la
 *       combinaison incohérente ;
 *   <li>la rentabilité ne <strong>répartit pas</strong> les dépenses non
 *       affectées : une assurance ne se divise pas par le nombre de ruches ;
 *   <li>la comparaison de saisons raisonne en <strong>années civiles</strong>,
 *       là où tous les autres agrégats glissent ;
 *   <li>l'export XLSX est un vrai classeur — un ZIP dont les cinq parties
 *       obligatoires sont présentes ;
 *   <li>les règles du SPRINT-22 acceptent des tâches sans ruche : maintenance
 *       d'un équipement, réapprovisionnement, archivage de saison.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ProductionStockIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("zumm/test-postgres:16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    private JwtRequestPostProcessor tenant(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_admin"),
                        new SimpleGrantedAuthority("ROLE_responsable"),
                        new SimpleGrantedAuthority("ROLE_superviseur"),
                        new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    private JwtRequestPostProcessor apiculteur(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    // ─── Produits autres que le miel ─────────────────────────────────────────

    @Test
    @DisplayName("un essaim se compte en unités, et la base refuse de le peser")
    void produitsAutresQueLeMiel() throws Exception {
        String t = "sp27-produits";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"dateRecolte":"2026-05-20","quantiteKg":2,
                                 "typeProduit":"essaim","unite":"unite"}""").formatted(rucheId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeProduit").value("essaim"))
                .andExpect(jsonPath("$.unite").value("unite"));

        // Cinq essaims ne pèsent pas cinq kilogrammes : la contrainte ferme
        // l'incohérence à la source plutôt que de laisser produire des totaux
        // faux.
        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"dateRecolte":"2026-05-21","quantiteKg":5,
                                 "typeProduit":"essaim","unite":"kg"}""").formatted(rucheId)))
                .andExpect(status().isConflict());

        // Sans précision, c'est du miel en kilogrammes : le modèle le
        // présupposait avant, et le défaut préserve les données existantes.
        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"dateRecolte":"2026-07-15","quantiteKg":18}""")
                                .formatted(rucheId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeProduit").value("miel"))
                .andExpect(jsonPath("$.unite").value("kg"));
    }

    // ─── Comptabilité ────────────────────────────────────────────────────────

    @Test
    @DisplayName("le bilan ne répartit pas les dépenses non affectées")
    void bilanSansCleDeRepartition() throws Exception {
        String t = "sp27-bilan";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        long premiere = ruche(t, siteId, fermeId);
        long seconde = ruche(t, siteId, fermeId);

        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-07-15","quantiteKg":20}""").formatted(premiere));
        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-07-15","quantiteKg":10}""").formatted(seconde));

        creer(t, "/api/depenses", ("""
                {"libelle":"Reine Buckfast","categorie":"cheptel","montantEur":45,
                 "dateDepense":"2026-04-10","rucheId":%d}""").formatted(premiere));
        creer(t, "/api/depenses", """
                {"libelle":"Assurance annuelle","categorie":"assurance","montantEur":300,
                 "dateDepense":"2026-01-05"}""");

        String bilan = mockMvc.perform(
                        get("/api/depenses/bilan?debut=2026-01-01&fin=2026-12-31").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionMielKg").value(30.0))
                .andExpect(jsonPath("$.depensesEur").value(345.0))
                // L'assurance figure ENTIÈRE et à part : la répartir par ruche
                // demanderait une clé inventée, et donnerait une rentabilité
                // plus jolie et moins vraie.
                .andExpect(jsonPath("$.depensesNonAffectees").value(300.0))
                .andReturn().getResponse().getContentAsString();

        var parRuche = json.readTree(bilan).get("parRuche");
        assertThat(parRuche).hasSize(2);
        // La première ruche porte SES 45 € et rien d'autre ; la seconde, zéro
        // dépense — pas 150 € d'assurance.
        for (var ligne : parRuche) {
            double depenses = ligne.get("depensesEur").asDouble();
            assertThat(depenses).isIn(45.0, 0.0);
        }

        // Une période inversée est une erreur d'appel, pas un bilan vide.
        mockMvc.perform(get("/api/depenses/bilan?debut=2026-12-31&fin=2026-01-01")
                        .with(tenant(t)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la comptabilité est fermée à un rôle de terrain")
    void comptabiliteReserveeAuPilotage() throws Exception {
        // Un apiculteur n'a pas à connaître le résultat de l'exploitation pour
        // tenir ses ruches.
        mockMvc.perform(get("/api/depenses").with(apiculteur("sp27-rbac")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/depenses/bilan?debut=2026-01-01&fin=2026-12-31")
                        .with(apiculteur("sp27-rbac")))
                .andExpect(status().isForbidden());
    }

    // ─── Saison contre saison ────────────────────────────────────────────────

    @Test
    @DisplayName("les saisons se comparent en années civiles, et le rendement compte les ruches qui ont produit")
    void comparaisonDeSaisons() throws Exception {
        String t = "sp27-saisons";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        long premiere = ruche(t, siteId, fermeId);
        long seconde = ruche(t, siteId, fermeId);

        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2025-07-10","quantiteKg":12}""").formatted(premiere));
        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-07-10","quantiteKg":20}""").formatted(premiere));
        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-07-10","quantiteKg":10}""").formatted(seconde));

        mockMvc.perform(get("/api/saisons").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // La plus récente d'abord.
                .andExpect(jsonPath("$[0].annee").value(2026))
                .andExpect(jsonPath("$[0].productionMielKg").value(30.0))
                .andExpect(jsonPath("$[0].ruchesProductives").value(2))
                // 30 kg sur DEUX ruches qui ont produit : c'est le rendement qui
                // dit si la saison fut bonne, pas le total.
                .andExpect(jsonPath("$[0].rendementKg").value(15.0))
                .andExpect(jsonPath("$[1].annee").value(2025))
                .andExpect(jsonPath("$[1].rendementKg").value(12.0));
    }

    // ─── Export ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("l'export XLSX est un vrai classeur, avec ses cinq parties obligatoires")
    void exportXlsx() throws Exception {
        String t = "sp27-export";
        long fermeId = ferme(t);
        ruche(t, site(t, fermeId), fermeId);

        byte[] classeur = mockMvc.perform(
                        get("/api/export/ruches?format=xlsx").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"zumm-ruches.xlsx\""))
                .andReturn().getResponse().getContentAsByteArray();

        List<String> parties = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(classeur))) {
            for (ZipEntry entree = zip.getNextEntry(); entree != null; entree = zip.getNextEntry()) {
                parties.add(entree.getName());
            }
        }
        // Les cinq parties que la spécification OOXML rend obligatoires. Sans
        // l'une d'elles, Excel refuse le fichier entier — et le test le dirait.
        assertThat(parties).containsExactlyInAnyOrder(
                "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels", "xl/worksheets/sheet1.xml");
    }

    @Test
    @DisplayName("l'export couvre quatorze ressources, et refuse celles qu'il ne connaît pas")
    void exportEtendu() throws Exception {
        String t = "sp27-export-liste";

        String liste = mockMvc.perform(get("/api/export/ressources").with(tenant(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(liste)).hasSize(14);

        for (String ressource : List.of("depenses", "materiels", "consommables", "recoltes")) {
            mockMvc.perform(get("/api/export/" + ressource + "?format=csv").with(tenant(t)))
                    .andExpect(status().isOk());
        }

        // Un nom inconnu est une erreur d'appel : un 404 laisserait croire à une
        // faute d'URL alors que c'est la ressource qui est fausse.
        mockMvc.perform(get("/api/export/licornes").with(tenant(t)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("l'export des sites ne porte aucune coordonnée")
    void exportSansPosition() throws Exception {
        String t = "sp27-export-position";
        site(t, ferme(t));

        String csv = new String(mockMvc.perform(get("/api/export/sites").with(tenant(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        // Un CSV circule, et il n'a pas de rôle porteur : `PolitiquePositions`
        // n'a aucune prise dessus. La position n'y est donc pas du tout.
        assertThat(csv).doesNotContain("44.5").doesNotContain("latitude");
    }

    // ─── Matériel, stock et règles ───────────────────────────────────────────

    @Test
    @DisplayName("un entretien fait repousse l'échéance et remet l'état à « bon »")
    void entretienMateriel() throws Exception {
        String t = "sp27-materiel";
        long materielId = creer(t, "/api/materiels", """
                {"libelle":"Extracteur 9 cadres","categorie":"extracteur","quantite":1,
                 "etat":"a_reviser","periodiciteJours":180,
                 "derniereMaintenance":"2025-01-01"}""");

        mockMvc.perform(get("/api/materiels/" + materielId).with(tenant(t)))
                .andExpect(status().isOk())
                // Échéance CALCULÉE : 2025-01-01 + 180 jours, donc largement
                // dépassée.
                .andExpect(jsonPath("$.prochaineMaintenance").value("2025-06-30"))
                .andExpect(jsonPath("$.enRetard").value(true));

        mockMvc.perform(post("/api/materiels/" + materielId + "/entretien?jour="
                        + LocalDate.now()).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enRetard").value(false))
                // Révisé n'est pas neuf : le prétendre ferait perdre la trace de
                // son âge.
                .andExpect(jsonPath("$.etat").value("bon"));

        // Un entretien ne se date pas dans l'avenir.
        mockMvc.perform(post("/api/materiels/" + materielId + "/entretien?jour="
                        + LocalDate.now().plusDays(1)).with(tenant(t)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un mouvement de stock ne descend pas sous zéro, et le refus dit ce qui reste")
    void mouvementDeStock() throws Exception {
        String t = "sp27-stock";
        long id = creer(t, "/api/consommables", """
                {"libelle":"Candi","categorie":"candi","quantite":10,"unite":"kg",
                 "seuilAlerte":3}""");

        mockMvc.perform(post("/api/consommables/" + id + "/mouvement?delta=-4").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantite").value(6.0))
                .andExpect(jsonPath("$.sousSeuil").value(false));

        mockMvc.perform(post("/api/consommables/" + id + "/mouvement?delta=-99").with(tenant(t)))
                .andExpect(status().isBadRequest());

        // Arriver PILE au seuil, c'est déjà être à court : la comparaison est
        // inclusive.
        mockMvc.perform(post("/api/consommables/" + id + "/mouvement?delta=-3").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantite").value(3.0))
                .andExpect(jsonPath("$.sousSeuil").value(true));
    }

    @Test
    @DisplayName("les règles engendrent des tâches sans ruche : matériel, stock, archivage")
    void reglesSansRuche() throws Exception {
        String t = "sp27-regles";
        creer(t, "/api/materiels", """
                {"libelle":"Extracteur","categorie":"extracteur","quantite":1,
                 "periodiciteJours":90,"derniereMaintenance":"2020-01-01"}""");
        creer(t, "/api/consommables", """
                {"libelle":"Cire gaufrée","categorie":"cire_gaufree","quantite":1,
                 "unite":"kg","seuilAlerte":5}""");

        String creees = mockMvc.perform(post("/api/regles/executer").with(tenant(t)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> codes = new ArrayList<>();
        json.readTree(creees).forEach(tache -> codes.add(tache.get("regleCode").asText()));
        assertThat(codes).contains("maintenance-materiel", "stock-bas");

        // Un second passage ne redouble rien : la clé de déclenchement et son
        // index unique partiel tiennent, y compris pour des tâches sans ruche.
        mockMvc.perform(post("/api/regles/executer").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Bilan annuel ────────────────────────────────────────────────────────

    @Test
    @DisplayName("le bilan annuel est un PDF, même pour une année sans récolte")
    void bilanAnnuelPdf() throws Exception {
        String t = "sp27-bilan-pdf";
        creer(t, "/api/depenses", """
                {"libelle":"Formation","categorie":"formation","montantEur":250,
                 "dateDepense":"2026-03-01"}""");

        byte[] pdf = mockMvc.perform(get("/api/saisons/2026/bilan.pdf").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andReturn().getResponse().getContentAsByteArray();

        // Une exploitation qui a dépensé sans récolter a précisément besoin de ce
        // document-là : le PDF sort quand même.
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    // ─── Jeu d'essai ─────────────────────────────────────────────────────────

    private long ferme(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        return creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
    }

    private long site(String t, long fermeId) throws Exception {
        return creer(t, "/api/sites", ("""
                {"nom":"S","fermeId":%d,"latitude":44.5,"longitude":1.5,
                 "dateMiseEnOeuvre":"2026-04-01"}""").formatted(fermeId));
    }

    private long ruche(String t, long siteId, long fermeId) throws Exception {
        return creer(t, "/api/ruches", ("""
                {"modele":"M","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10}]}""")
                .formatted(siteId, fermeId));
    }

    private long creer(String t, String url, String corps) throws Exception {
        MockHttpServletRequestBuilder requete = post(url).with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON).content(corps);
        String rep = mockMvc.perform(requete)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(rep).get("id").asLong();
    }
}
