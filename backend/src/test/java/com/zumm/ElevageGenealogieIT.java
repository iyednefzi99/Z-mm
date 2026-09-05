package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
 * Éprouve le SPRINT-29, lot D — élevage, reines et généalogie.
 *
 * <p>Ce qui mérite un test d'intégration ici est ce qui décide de la valeur des
 * chiffres rendus :
 *
 * <ol>
 *   <li>l'arbre se lit dans les deux sens, et une lignée circulaire est
 *       <strong>refusée</strong> — la base n'en vérifie qu'un pas ;
 *   <li>l'index génétique est <strong>borné au règne</strong> : une récolte
 *       antérieure à l'introduction appartient à la reine précédente ;
 *   <li>un critère sans assez d'observations vaut {@code null}, et il n'y a
 *       <strong>aucune note globale</strong> ;
 *   <li>le dossier de conformité <strong>ne certifie pas</strong> : ce qu'il
 *       ignore sort en « à justifier », jamais en « conforme » ;
 *   <li>et le correctif de clés étrangères de la V29 : supprimer une ruche
 *       référencée détache la référence au lieu d'échouer.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ElevageGenealogieIT {

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

    // ─── Généalogie ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("l'arbre se lit dans les deux sens, sur trois générations")
    void arbreDeLignee() throws Exception {
        String t = "sp29-arbre";
        long aieule = creer(t, "/api/elevage/reines", """
                {"code":"R-2024-01","origine":"achat","fournisseur":"Rucher du Causse",
                 "race":"Buckfast","anneeNaissance":2024}""");
        long mere = creer(t, "/api/elevage/reines", ("""
                {"code":"R-2025-07","origine":"elevage","mereId":%d,"anneeNaissance":2025}""")
                .formatted(aieule));
        long fille = creer(t, "/api/elevage/reines", ("""
                {"code":"R-2026-12","origine":"elevage","mereId":%d,"anneeNaissance":2026}""")
                .formatted(mere));
        creer(t, "/api/elevage/reines", ("""
                {"code":"R-2026-13","origine":"elevage","mereId":%d,"anneeNaissance":2026}""")
                .formatted(mere));

        // Depuis la fille : la chaîne des mères remonte, une par génération.
        mockMvc.perform(get("/api/elevage/reines/" + fille + "/genealogie").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ascendants.length()").value(2))
                .andExpect(jsonPath("$.ascendants[0].code").value("R-2025-07"))
                .andExpect(jsonPath("$.ascendants[0].profondeur").value(1))
                .andExpect(jsonPath("$.ascendants[1].code").value("R-2024-01"))
                .andExpect(jsonPath("$.ascendants[1].profondeur").value(2))
                .andExpect(jsonPath("$.descendants.length()").value(0));

        // Depuis l'aïeule : trois descendantes, la petite-fille à la profondeur 2.
        mockMvc.perform(get("/api/elevage/reines/" + aieule + "/genealogie").with(tenant(t)))
                .andExpect(jsonPath("$.descendants.length()").value(3))
                .andExpect(jsonPath("$.descendants[0].profondeur").value(1))
                .andExpect(jsonPath("$.descendants[1].profondeur").value(2))
                .andExpect(jsonPath("$.ascendants.length()").value(0));
    }

    @Test
    @DisplayName("une lignée circulaire est refusée, au-delà du seul pas que voit la base")
    void lignéeCirculaireRefusee() throws Exception {
        String t = "sp29-cycle";
        long grandMere = creer(t, "/api/elevage/reines", """
                {"code":"C-1","origine":"elevage"}""");
        long mere = creer(t, "/api/elevage/reines", ("""
                {"code":"C-2","origine":"elevage","mereId":%d}""").formatted(grandMere));

        // Faire descendre la grand-mère de sa propre fille fermerait la boucle :
        // toute lecture d'arbre tournerait ensuite indéfiniment.
        mockMvc.perform(put("/api/elevage/reines/" + grandMere).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"code":"C-1","origine":"elevage","mereId":%d}""").formatted(mere)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("le fournisseur ne survit pas à une origine qui n'est pas l'achat")
    void fournisseurReserveALAchat() throws Exception {
        String t = "sp29-fournisseur";

        // La base refuse un fournisseur hors achat ; le service l'efface plutôt
        // que de faire échouer une saisie où l'utilisateur a changé d'origine.
        mockMvc.perform(post("/api/elevage/reines").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"origine":"essaimage","fournisseur":"Rucher du Causse"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fournisseur").doesNotExist());
    }

    // ─── Séries d'élevage ────────────────────────────────────────────────────

    @Test
    @DisplayName("l'entonnoir d'une série ne remonte pas, et les taux se calculent")
    void serieDElevage() throws Exception {
        String t = "sp29-serie";

        String rep = mockMvc.perform(post("/api/elevage/series").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Greffage du 12 mai","dateGreffage":"2026-05-12",
                                 "methode":"greffage","nbGreffees":40,"nbAcceptees":31,
                                 "nbNees":28,"nbFecondees":22}"""))
                .andExpect(status().isCreated())
                // Calculés, jamais stockés : deux colonnes voisines suffisent.
                .andExpect(jsonPath("$.tauxAcceptation").value(78))
                .andExpect(jsonPath("$.tauxReussite").value(55))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(rep).get("id").asLong()).isPositive();

        // Plus d'acceptées que de greffées : une faute de frappe qui donnerait un
        // taux d'acceptation au-dessus de 100 %.
        mockMvc.perform(post("/api/elevage/series").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Impossible","dateGreffage":"2026-05-12",
                                 "nbGreffees":10,"nbAcceptees":12}"""))
                .andExpect(status().isConflict());
    }

    // ─── Index génétique ─────────────────────────────────────────────────────

    @Test
    @DisplayName("l'index ne compte que ce qui s'est passé pendant le règne")
    void indexBorneAuRegne() throws Exception {
        String t = "sp29-index";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        // Une récolte AVANT l'introduction : elle appartient à la précédente.
        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-05-10","quantiteKg":25}""").formatted(rucheId));
        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-08-10","quantiteKg":12}""").formatted(rucheId));

        long reineId = creer(t, "/api/elevage/reines", ("""
                {"code":"I-1","origine":"elevage","rucheId":%d,"dateIntroduction":"2026-06-01"}""")
                .formatted(rucheId));

        mockMvc.perform(get("/api/elevage/reines/" + reineId + "/index").with(tenant(t)))
                .andExpect(status().isOk())
                // 12 et non 37 : sans cette borne, une reine posée en juillet
                // hériterait de la récolte de printemps de la précédente, et le
                // classement de l'éleveur serait exactement inversé.
                .andExpect(jsonPath("$.criteres[?(@.code == 'production')].valeur").value(12.0))
                .andExpect(jsonPath("$.criteres[?(@.code == 'production')].observations").value(1));
    }

    @Test
    @DisplayName("un critère sans assez d'observations vaut null, et rien n'est agrégé")
    void aucuneNoteGlobale() throws Exception {
        String t = "sp29-criteres";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);
        long agentId = agent(t);
        long reineId = creer(t, "/api/elevage/reines", ("""
                {"code":"N-1","origine":"elevage","rucheId":%d,"dateIntroduction":"2026-04-01"}""")
                .formatted(rucheId));

        // Deux visites seulement : la douceur en demande trois. Une douceur
        // mesurée sur un jour de vent n'est pas une douceur.
        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-02",
                 "observation":{"temperament":"doux"}}""").formatted(rucheId, agentId));
        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-20",
                 "observation":{"temperament":"doux"}}""").formatted(rucheId, agentId));

        mockMvc.perform(get("/api/elevage/reines/" + reineId + "/index").with(tenant(t)))
                .andExpect(jsonPath("$.criteres[?(@.code == 'douceur')].suffisant").value(false))
                .andExpect(jsonPath("$.criteres[?(@.code == 'douceur' && @.valeur != null)]")
                        .isEmpty())
                // Le test hygiénique est entré au référentiel fermé par la V29 :
                // tant que personne ne l'a relevé, il se dit insuffisant.
                .andExpect(jsonPath("$.criteres[?(@.code == 'hygiene')].observations").value(0))
                // AUCUNE note globale : le document ne porte pas de champ agrégé,
                // et il ne doit jamais en porter.
                .andExpect(jsonPath("$.note").doesNotExist())
                .andExpect(jsonPath("$.score").doesNotExist());

        // La troisième visite fait passer la douceur au calcul.
        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-06-02",
                 "observation":{"temperament":"normal"}}""").formatted(rucheId, agentId));

        mockMvc.perform(get("/api/elevage/reines/" + reineId + "/index").with(tenant(t)))
                .andExpect(jsonPath("$.criteres[?(@.code == 'douceur')].suffisant").value(true))
                .andExpect(jsonPath("$.criteres[?(@.code == 'douceur')].valeur").value(2.7));
    }

    @Test
    @DisplayName("une reine sans ruche n'a pas d'index, et le dit plutôt que de rendre des zéros")
    void reineSansRuche() throws Exception {
        String t = "sp29-banque";
        long reineId = creer(t, "/api/elevage/reines", """
                {"code":"B-1","origine":"elevage"}""");

        mockMvc.perform(get("/api/elevage/reines/" + reineId + "/index").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rucheId").doesNotExist())
                .andExpect(jsonPath("$.criteres.length()").value(0));
    }

    // ─── Documents ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("le registre d'élevage sort en PDF, même vide")
    void registreEnPdf() throws Exception {
        String t = "sp29-registre";

        byte[] pdf = mockMvc.perform(get("/api/elevage/registre.pdf").with(tenant(t))
                        .param("depuis", "2026-01-01").param("jusqu", "2026-12-31"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        // Un registre vide se présente devant un contrôle ; il ne se refuse pas.
        assertThat(pdf.length).isGreaterThan(500);
    }

    @Test
    @DisplayName("le dossier de conformité ne certifie rien, et nomme ce qu'il ignore")
    void dossierDeConformite() throws Exception {
        String t = "sp29-bio";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);
        long agentId = agent(t);

        creer(t, "/api/traitements", ("""
                {"rucheId":%d,"agentId":%d,"produit":"Apivar","substanceActive":"amitraze",
                 "cible":"varroa","dateDebut":"2026-08-01"}""").formatted(rucheId, agentId));

        mockMvc.perform(get("/api/elevage/conformite").with(tenant(t))
                        .param("depuis", "2026-01-01").param("jusqu", "2026-12-31"))
                .andExpect(status().isOk())
                // L'avertissement voyage avec le dossier : un PDF circule sans la
                // page qui l'a produit.
                .andExpect(jsonPath("$.avertissement").value(
                        org.hamcrest.Matchers.containsString("ne certifie pas")))
                // L'amitraze n'est pas admise en apiculture biologique : signalé,
                // et non déclaré interdit — c'est au contrôleur de trancher.
                .andExpect(jsonPath("$.points[?(@.code == 'traitements')].statut")
                        .value("signale"))
                // L'origine des sucres ne figure NULLE PART dans le système : la
                // compter comme conforme serait un mensonge par omission.
                .andExpect(jsonPath("$.points[?(@.code == 'nourrissement')].statut")
                        .value("a_justifier"));
    }

    // ─── Le correctif de clés étrangères de la V29 ───────────────────────────

    @Test
    @DisplayName("supprimer une ruche référencée détache la référence au lieu d'échouer")
    void suppressionDUneRucheReferencee() throws Exception {
        String t = "sp29-fk";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        long rucheId = ruche(t, siteId, fermeId);

        creer(t, "/api/depenses", ("""
                {"libelle":"Reine Buckfast","categorie":"cheptel","montantEur":45,
                 "dateDepense":"2026-04-10","rucheId":%d}""").formatted(rucheId));

        // Avant la V29, `ON DELETE SET NULL` sans liste de colonnes mettait AUSSI
        // `tenant_id` à NULL : la suppression échouait en 500, sur un message
        // parlant de `tenant_id` — au dernier endroit où l'on aurait cherché.
        mockMvc.perform(delete("/api/ruches/" + rucheId).with(tenant(t)))
                .andExpect(status().isNoContent());

        // La dépense survit, détachée : elle a bien été engagée.
        mockMvc.perform(get("/api/depenses").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rucheId").doesNotExist());
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

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

    private long agent(String t) throws Exception {
        return creer(t, "/api/agents", """
                {"nom":"Agent","role":"apiculteur","email":"agent@exemple.test"}""");
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
