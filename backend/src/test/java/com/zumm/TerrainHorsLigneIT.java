package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
 * Éprouve le SPRINT-24, lot C — le terrain sans réseau — contre un PostgreSQL
 * réel.
 *
 * <p>Cinq comportements s'y jouent, et ce sont les cinq par lesquels une
 * fonction hors ligne se met à mentir :
 *
 * <ol>
 *   <li>l'emport est <strong>daté par le serveur</strong> et rendu en UN appel :
 *       c'est ce qui empêche une donnée du disque de passer pour fraîche, et un
 *       emport à moitié fait d'avoir l'air complet ;
 *   <li>l'emport <strong>passe par la politique de positions</strong> — ce
 *       serait l'endroit idéal pour qu'une adresse exacte s'échappe vers un
 *       appareil sans session ;
 *   <li>un brouillon est <strong>unique par (agent, ruche)</strong> et ne
 *       devient jamais une visite tout seul ;
 *   <li>une modification portant une version <strong>périmée</strong> est
 *       refusée en 409, et la réponse dit la version du serveur — sans quoi le
 *       rejeu de la file écraserait le travail d'un autre agent en silence ;
 *   <li>la <strong>fiche d'inspection vierge</strong> est un vrai PDF, servi en
 *       ligne, avec une ligne par ruche.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TerrainHorsLigneIT {

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

    /** Profil de terrain : position arrondie, adresse retirée. */
    private JwtRequestPostProcessor apiculteur(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    // ─── Emport d'un rucher ──────────────────────────────────────────────────

    @Test
    @DisplayName("l'emport rend le rucher entier en un appel, daté par le serveur")
    void emportComplet() throws Exception {
        String t = "sp24-emport";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, "Rucher du causse");
        long agentId = agent(t);
        long premiere = ruche(t, siteId, fermeId);
        ruche(t, siteId, fermeId);

        // Une tâche ouverte, un traitement sous carence : les deux seules données
        // de l'emport qui INTERDISENT un geste au rucher.
        creer(t, "/api/taches", ("""
                {"libelle":"Poser les hausses","rucheId":%d,"agentId":%d,
                 "echeance":"%s","faite":false,"priorite":"haute","categorie":"materiel"}""")
                .formatted(premiere, agentId, LocalDate.now().plusDays(3)));
        creer(t, "/api/traitements", ("""
                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                 "dateDebut":"%s","delaiCarenceJours":365}""")
                .formatted(premiere, agentId, LocalDate.now()));

        mockMvc.perform(get("/api/ruchers/" + siteId + "/emport").with(tenant(t)))
                .andExpect(status().isOk())
                // Le serveur date l'instantané : c'est ce qui permet à l'écran
                // d'afficher « données du 2 septembre à 14 h 12 » plutôt que de
                // laisser croire à une lecture fraîche.
                .andExpect(jsonPath("$.preleveLe").exists())
                .andExpect(jsonPath("$.site.nom").value("Rucher du causse"))
                .andExpect(jsonPath("$.ruches.length()").value(2))
                .andExpect(jsonPath("$.tachesOuvertes.length()").value(1))
                .andExpect(jsonPath("$.sousCarence.length()").value(1))
                // Les mesures de capteurs restent DEHORS : une courbe de poids
                // figée trompe là où une liste de ruches ne trompe pas. C'est
                // l'objection de `vite.config.ts`, et elle tient.
                .andExpect(jsonPath("$.mesures").doesNotExist())
                .andExpect(jsonPath("$.meteo").doesNotExist());
    }

    @Test
    @DisplayName("l'emport masque l'adresse comme toute autre sortie")
    void emportMasquePosition() throws Exception {
        String t = "sp24-emport-masque";
        long fermeId = ferme(t);
        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Rucher des tilleuls","fermeId":%d,"latitude":44.447,"longitude":1.441,
                 "dateMiseEnOeuvre":"2026-04-01","adresseRue":"12 chemin des Vignes",
                 "codePostal":"46100","ville":"Figeac","pays":"FR"}""").formatted(fermeId));

        // Emporter un rucher serait le moyen idéal d'obtenir en clair, sur un
        // appareil sans session, ce que l'API dégrade en ligne.
        mockMvc.perform(get("/api/ruchers/" + siteId + "/emport").with(apiculteur(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.site.adresseRue").doesNotExist())
                .andExpect(jsonPath("$.site.codePostal").doesNotExist())
                .andExpect(jsonPath("$.site.ville").value("Figeac"));
    }

    @Test
    @DisplayName("emporter un rucher inconnu est un 404, pas un instantané vide")
    void emportRucherInconnu() throws Exception {
        // Un instantané vide serait indiscernable d'un rucher sans ruche, et
        // l'apiculteur partirait avec.
        mockMvc.perform(get("/api/ruchers/999999/emport").with(tenant("sp24-404")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la couverture réseau se saisit, se lit, et refuse une valeur inventée")
    void couvertureReseau() throws Exception {
        String t = "sp24-couverture";
        long fermeId = ferme(t);
        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Zone blanche","fermeId":%d,"latitude":44.9,"longitude":1.9,
                 "dateMiseEnOeuvre":"2026-04-01","couvertureReseau":"aucune"}""")
                .formatted(fermeId));

        mockMvc.perform(get("/api/sites/" + siteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couvertureReseau").value("aucune"));

        // Absente, elle reste INCONNUE : un défaut à « correcte » ferait partir
        // un apiculteur sans emport sur un rucher en zone blanche.
        long sansReponse = site(t, fermeId, "Jamais visité");
        mockMvc.perform(get("/api/sites/" + sansReponse).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couvertureReseau").doesNotExist());

        mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"nom":"S","fermeId":%d,"latitude":44.0,"longitude":1.0,
                                 "dateMiseEnOeuvre":"2026-04-01","couvertureReseau":"excellente"}""")
                                .formatted(fermeId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Brouillon de visite ─────────────────────────────────────────────────

    @Test
    @DisplayName("un brouillon est unique par agent et par ruche : le dernier appareil gagne")
    void brouillonUnique() throws Exception {
        String t = "sp24-brouillon";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, "Rucher");
        long agentId = agent(t);
        long rucheId = ruche(t, siteId, fermeId);

        String premier = mockMvc.perform(put("/api/brouillons").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"agentId":%d,"rucheId":%d,
                                 "contenu":"{\\"constatations\\":\\"trois cadres de couvain\\"}",
                                 "appareil":"telephone"}""").formatted(agentId, rucheId)))
                // 200 et non 201 : déposer un brouillon est idempotent sur la
                // paire (agent, ruche), et un 201 à chaque frappe sauvegardée
                // laisserait croire à une création.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appareil").value("telephone"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(premier).get("id").asLong();

        mockMvc.perform(put("/api/brouillons").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"agentId":%d,"rucheId":%d,
                                 "contenu":"{\\"constatations\\":\\"cinq cadres\\"}",
                                 "appareil":"ordinateur du local"}""").formatted(agentId, rucheId)))
                .andExpect(status().isOk())
                // Le MÊME brouillon : deux ne répondraient à aucune question,
                // ils poseraient celle de savoir lequel reprendre.
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.appareil").value("ordinateur du local"));

        mockMvc.perform(get("/api/brouillons?agentId=" + agentId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rucheModele").value("M"))
                .andExpect(jsonPath("$[0].contenu").value(
                        "{\"constatations\":\"cinq cadres\"}"));

        // Un brouillon ne devient jamais une visite tout seul : le registre est
        // resté vide.
        mockMvc.perform(get("/api/visites").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/brouillons/" + id).with(tenant(t)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/brouillons?agentId=" + agentId).with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("le serveur ne valide pas le contenu d'un brouillon : une saisie en cours est incomplète")
    void brouillonNonValide() throws Exception {
        String t = "sp24-brouillon-libre";
        long fermeId = ferme(t);
        long agentId = agent(t);
        long rucheId = ruche(t, site(t, fermeId, "Rucher"), fermeId);

        // Ce contenu ne serait accepté par aucune route de visite. C'est le
        // point : refuser une saisie tant qu'elle n'est pas complète ferait
        // perdre exactement ce qu'on cherche à sauver.
        mockMvc.perform(put("/api/brouillons").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"agentId":%d,"rucheId":%d,"contenu":"pas du JSON du tout",
                                 "appareil":null}""").formatted(agentId, rucheId)))
                .andExpect(status().isOk());
    }

    // ─── Conflit de version ──────────────────────────────────────────────────

    @Test
    @DisplayName("une modification sur une version périmée est refusée en 409, avec la version du serveur")
    void conflitDeVersion() throws Exception {
        String t = "sp24-conflit";
        long fermeId = ferme(t);
        long agentId = agent(t);
        long rucheId = ruche(t, site(t, fermeId, "Rucher"), fermeId);

        String corps = ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-08-20","raison":"controle",
                 "constatations":"colonie calme"}""").formatted(rucheId, agentId);
        long visiteId = creer(t, "/api/visites", corps);

        // Sans en-tête, la garde ne joue pas : les écrans en ligne modifient ce
        // qu'ils viennent de lire, leur imposer une précondition n'aurait
        // protégé personne.
        mockMvc.perform(put("/api/visites/" + visiteId).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());

        // Version d'avant la dernière écriture : c'est le rejeu d'une saisie
        // faite au rucher pendant qu'un autre agent enregistrait la sienne.
        String reponse = mockMvc.perform(put("/api/visites/" + visiteId).with(tenant(t))
                        .header("X-Zumm-Version", "2020-01-01T00:00:00Z")
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        // La réponse porte la version du serveur : sans elle, l'interface ne
        // pourrait proposer qu'un message d'échec, jamais un choix.
        assertThat(json.readTree(reponse).get("versionServeur").asText()).isNotBlank();

        // Avec la version courante, la modification passe.
        String versionCourante = json.readTree(
                mockMvc.perform(get("/api/visites/" + visiteId).with(tenant(t)))
                        .andReturn().getResponse().getContentAsString())
                .get("majLe").asText();
        mockMvc.perform(put("/api/visites/" + visiteId).with(tenant(t))
                        .header("X-Zumm-Version", versionCourante)
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isOk());
    }

    // ─── Fiche d'inspection vierge ───────────────────────────────────────────

    @Test
    @DisplayName("la fiche d'inspection vierge est un PDF servi en ligne, nommant chaque ruche")
    void ficheInspectionVierge() throws Exception {
        String t = "sp24-fiche";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, "Rucher des tilleuls");
        ruche(t, siteId, fermeId);
        ruche(t, siteId, fermeId);

        byte[] pdf = mockMvc.perform(
                        get("/api/ruchers/" + siteId + "/fiche-inspection.pdf").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                // `inline` : la fiche se relit à l'écran avant d'être imprimée.
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"fiche-inspection-%d.pdf\"".formatted(siteId)))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        // Un PDF vide passerait les assertions d'en-tête sans rien contenir.
        assertThat(pdf.length).isGreaterThan(1000);
    }

    // ─── Jeu d'essai ─────────────────────────────────────────────────────────

    private long ferme(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        return creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
    }

    private long site(String t, long fermeId, String nom) throws Exception {
        return creer(t, "/api/sites", ("""
                {"nom":"%s","fermeId":%d,"latitude":44.5,"longitude":1.5,
                 "dateMiseEnOeuvre":"2026-04-01"}""").formatted(nom, fermeId));
    }

    private long ruche(String t, long siteId, long fermeId) throws Exception {
        return creer(t, "/api/ruches", ("""
                {"modele":"M","siteId":%d,"fermeId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10}]}""")
                .formatted(siteId, fermeId));
    }

    private long agent(String t) throws Exception {
        return creer(t, "/api/agents",
                "{\"nom\":\"Amine Trabelsi\",\"role\":\"apiculteur\",\"email\":null}");
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
