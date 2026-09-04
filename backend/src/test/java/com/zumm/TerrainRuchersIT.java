package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.hamcrest.Matchers;
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
 * Verifie le SPRINT-21 contre un PostgreSQL reel : identite postale du rucher,
 * ressources florales, historique d'emplacement, divisions, captures d'essaim,
 * photos multi-cibles, recherche transverse et export iCalendar.
 *
 * <p>Ce lot repond au §1 de {@code docs/ECART-CONCURRENTS.md}. Ce qu'on eprouve
 * ici n'est pas le CRUD — identique a vingt autres — mais les six regles qui ne
 * se voient qu'en base ou qu'a la sortie :
 *
 * <ol>
 *   <li>l'adresse postale sort <strong>masquee</strong> pour un profil non
 *       proprietaire, plus fort que les coordonnees ;
 *   <li>un demenagement clot l'emplacement courant et en ouvre un seul autre —
 *       l'index unique partiel refuse deux emplacements ouverts ;
 *   <li>une ruche n'a qu'une mere ({@code uq_division_fille}) ;
 *   <li>loger une capture pose l'origine de la ruche, sans jamais l'ecraser ;
 *   <li>une photo a exactement une cible ({@code ck_photo_cible_unique}) ;
 *   <li>le {@code .ics} ne porte aucune position, et la recherche non plus.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TerrainRuchersIT {

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

    /** Profil de pilotage : il voit la position exacte, donc l'adresse complete. */
    private JwtRequestPostProcessor tenant(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_admin"),
                        new SimpleGrantedAuthority("ROLE_responsable"),
                        new SimpleGrantedAuthority("ROLE_superviseur"),
                        new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    /** Profil de terrain : position arrondie, adresse retiree. */
    private JwtRequestPostProcessor apiculteur(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    // ─── Identite postale et ressources florales ─────────────────────────────

    @Test
    @DisplayName("un rucher porte son adresse, son type, son exposition et ses sources de nectar")
    void identitePostaleEtRessources() throws Exception {
        String t = "sp21-identite";
        long fermeId = ferme(t);

        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Rucher des tilleuls","fermeId":%d,
                 "latitude":44.447,"longitude":1.441,"dateMiseEnOeuvre":"2026-04-01",
                 "adresseRue":"12 chemin des Vignes","codePostal":"46100","ville":"Figeac",
                 "pays":"FR","typeSite":"sedentaire","exposition":"sud_est",
                 "ressources":[{"ressource":"tilleul","distanceM":800,"note":null},
                               {"ressource":"acacia","distanceM":2500,"note":null}]}""")
                .formatted(fermeId));

        mockMvc.perform(get("/api/sites/" + siteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adresseRue").value("12 chemin des Vignes"))
                .andExpect(jsonPath("$.ville").value("Figeac"))
                .andExpect(jsonPath("$.pays").value("FR"))
                .andExpect(jsonPath("$.typeSite").value("sedentaire"))
                .andExpect(jsonPath("$.exposition").value("sud_est"))
                // Triees par ressource : « acacia » avant « tilleul ».
                .andExpect(jsonPath("$.ressources.length()").value(2))
                .andExpect(jsonPath("$.ressources[0].ressource").value("acacia"))
                .andExpect(jsonPath("$.ressources[0].distanceM").value(2500));
    }

    @Test
    @DisplayName("l'adresse est masquee pour un profil non proprietaire, la commune non")
    void adresseMasquee() throws Exception {
        String t = "sp21-masque";
        long fermeId = ferme(t);
        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Rucher du causse","fermeId":%d,
                 "latitude":44.447,"longitude":1.441,"dateMiseEnOeuvre":"2026-04-01",
                 "adresseRue":"12 chemin des Vignes","codePostal":"46100","ville":"Figeac",
                 "pays":"FR"}""").formatted(fermeId));

        // Ce qu'on eprouve ici est le MASQUE applicatif, pas la portee : en test,
        // l'application se connecte avec le role PROPRIETAIRE de la base, qui
        // contourne la RLS meme sous FORCE (voir l'en-tete de PorteeAgentIT). La
        // restriction par affectation a son propre test, sous `zumm_app`.
        mockMvc.perform(get("/api/sites/" + siteId).with(apiculteur(t)))
                .andExpect(status().isOk())
                // Une rue situe un rucher au portail la ou deux decimales le
                // situent au kilometre : elle disparait avec l'altitude.
                .andExpect(jsonPath("$.adresseRue").doesNotExist())
                .andExpect(jsonPath("$.codePostal").doesNotExist())
                .andExpect(jsonPath("$.altitude").doesNotExist())
                // La commune reste : c'est la maille a laquelle un agent s'y rend.
                .andExpect(jsonPath("$.ville").value("Figeac"))
                .andExpect(jsonPath("$.pays").value("FR"));
    }

    @Test
    @DisplayName("un code pays hors ISO est refuse avant la base")
    void paysHorsReferentiel() throws Exception {
        String t = "sp21-pays";
        long fermeId = ferme(t);

        mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"nom":"S","fermeId":%d,"latitude":44.0,"longitude":1.0,
                                 "dateMiseEnOeuvre":"2026-04-01","pays":"France"}""")
                                .formatted(fermeId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Historique d'emplacement ────────────────────────────────────────────

    @Test
    @DisplayName("la creation d'un rucher ouvre son premier emplacement")
    void emplacementInitial() throws Exception {
        String t = "sp21-emplacement";
        long siteId = site(t);

        mockMvc.perform(get("/api/sites/" + siteId + "/emplacements").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].courant").value(true))
                .andExpect(jsonPath("$[0].motif").value("installation"))
                .andExpect(jsonPath("$[0].dateFin").doesNotExist());
    }

    @Test
    @DisplayName("un demenagement clot l'emplacement courant et en ouvre un seul autre")
    void demenagement() throws Exception {
        String t = "sp21-transhumance";
        long siteId = site(t);

        mockMvc.perform(post("/api/sites/" + siteId + "/demenagement").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":43.9,"longitude":1.2,"altitude":320,
                                 "dateDebut":"2026-05-15","motif":"miellee",
                                 "note":"Montee sur le plateau"}"""))
                .andExpect(status().isOk())
                // La position COURANTE du site suit : toutes les requetes spatiales
                // la lisent, et les faire passer par l'historique les ralentirait.
                .andExpect(jsonPath("$.latitude").value(43.9))
                .andExpect(jsonPath("$.dateDemenagement").value("2026-05-15"));

        mockMvc.perform(get("/api/sites/" + siteId + "/emplacements").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Le plus recent d'abord, et un seul emplacement ouvert : un rucher
                // n'est jamais a deux endroits ni nulle part entre les deux.
                .andExpect(jsonPath("$[0].courant").value(true))
                .andExpect(jsonPath("$[0].motif").value("miellee"))
                .andExpect(jsonPath("$[1].courant").value(false))
                .andExpect(jsonPath("$[1].dateFin").value("2026-05-15"));
    }

    @Test
    @DisplayName("un emplacement anterieur au courant est refuse, avec un message")
    void demenagementAntidate() throws Exception {
        String t = "sp21-antidate";
        long siteId = site(t);

        mockMvc.perform(post("/api/sites/" + siteId + "/demenagement").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"latitude":43.9,"longitude":1.2,"altitude":null,
                                 "dateDebut":"2026-01-01","motif":"transhumance","note":null}"""))
                .andExpect(status().isBadRequest());
    }

    // ─── Divisions et filiation ──────────────────────────────────────────────

    @Test
    @DisplayName("une division relie mere et fille, et pose l'origine de la fille")
    void divisionEtFiliation() throws Exception {
        String t = "sp21-division";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId);
        long mereId = ruche(t, siteId, fermeId);
        long filleId = ruche(t, siteId, fermeId);
        long agentId = agent(t);

        mockMvc.perform(post("/api/divisions").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheMereId":%d,"rucheFilleId":%d,"agentId":%d,
                                 "dateDivision":"2026-05-12","methode":"essaim_artificiel",
                                 "cadresCouvain":3,"cadresProvisions":2,
                                 "origineReine":"cellule_royale"}""")
                                .formatted(mereId, filleId, agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rucheMereId").value(mereId))
                .andExpect(jsonPath("$.rucheFilleId").value(filleId))
                .andExpect(jsonPath("$.cadresCouvain").value(3));

        // L'origine de la fille est ALIGNEE sur l'evenement qui l'explique : la
        // laisser vide ferait mentir la statistique par origine du SPRINT-20.
        mockMvc.perform(get("/api/ruches/" + filleId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origine").value("division"));

        // La filiation se lit dans les deux sens depuis l'une ou l'autre.
        mockMvc.perform(get("/api/divisions/filiation").with(tenant(t))
                        .param("rucheId", String.valueOf(filleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rucheMereId").value(mereId));
    }

    @Test
    @DisplayName("une ruche n'a qu'une seule mere")
    void filleDejaIssueDUneDivision() throws Exception {
        String t = "sp21-mere-unique";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId);
        long mereId = ruche(t, siteId, fermeId);
        long autreMereId = ruche(t, siteId, fermeId);
        long filleId = ruche(t, siteId, fermeId);
        long agentId = agent(t);

        creer(t, "/api/divisions", ("""
                {"rucheMereId":%d,"rucheFilleId":%d,"agentId":%d,"dateDivision":"2026-05-12"}""")
                .formatted(mereId, filleId, agentId));

        // Un arbre genealogique a deux meres serait une saisie fautive, pas une
        // richesse : le service le refuse en 400 plutot que la base en 500.
        mockMvc.perform(post("/api/divisions").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheMereId":%d,"rucheFilleId":%d,"agentId":%d,
                                 "dateDivision":"2026-06-01"}""")
                                .formatted(autreMereId, filleId, agentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une ruche ne se divise pas vers elle-meme")
    void divisionCirculaire() throws Exception {
        String t = "sp21-circulaire";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId);
        long rucheId = ruche(t, siteId, fermeId);
        long agentId = agent(t);

        mockMvc.perform(post("/api/divisions").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheMereId":%d,"rucheFilleId":%d,"agentId":%d,
                                 "dateDivision":"2026-05-12"}""")
                                .formatted(rucheId, rucheId, agentId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Captures d'essaim ───────────────────────────────────────────────────

    @Test
    @DisplayName("une capture existe avant sa ruche, et n'est logee que plus tard")
    void captureEtLogement() throws Exception {
        String t = "sp21-capture";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId);
        long rucheId = ruche(t, siteId, fermeId);
        long agentId = agent(t);

        long captureId = creer(t, "/api/captures", ("""
                {"agentId":%d,"dateCapture":"2026-05-04","origine":"essaim_naturel",
                 "lieu":"Haie du voisin, chemin des Vignes","poidsKg":1.8,"hauteurM":3.5}""")
                .formatted(agentId));

        mockMvc.perform(get("/api/captures").with(tenant(t)).param("enAttente", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].logee").value(false));

        mockMvc.perform(post("/api/captures/" + captureId + "/loger").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logee").value(true))
                .andExpect(jsonPath("$.rucheId").value(rucheId));

        // Comme pour une division, l'origine de la ruche suit l'evenement.
        mockMvc.perform(get("/api/ruches/" + rucheId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origine").value("essaim_capture"));

        mockMvc.perform(get("/api/captures").with(tenant(t)).param("enAttente", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Photos multi-cibles ─────────────────────────────────────────────────

    @Test
    @DisplayName("une photo s'attache a une ruche comme a un rucher, et jamais aux deux")
    void photoSurAutreChoseQuUneVisite() throws Exception {
        String t = "sp21-photo";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId);
        long rucheId = ruche(t, siteId, fermeId);

        creer(t, "/api/photos", ("""
                {"cible":"RUCHE","cibleId":%d,"url":"/photos/ruche.jpg","legende":"Cadre de couvain"}""")
                .formatted(rucheId));
        creer(t, "/api/photos", ("""
                {"cible":"SITE","cibleId":%d,"url":"/photos/rucher.jpg","legende":null}""")
                .formatted(siteId));

        mockMvc.perform(get("/api/photos").with(tenant(t))
                        .param("cible", "RUCHE").param("cibleId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cible").value("RUCHE"))
                .andExpect(jsonPath("$[0].cibleId").value(rucheId));

        // Une cible inexistante est refusee en 400, pas en 500 : la cle etrangere
        // composite dirait la meme chose, mais sans nommer l'objet manquant.
        mockMvc.perform(post("/api/photos").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cible":"RUCHE","cibleId":999999,"url":"/p.jpg","legende":null}"""))
                .andExpect(status().isBadRequest());
    }

    // ─── Recherche transverse ────────────────────────────────────────────────

    @Test
    @DisplayName("la recherche traverse les familles sans jamais rendre de position")
    void rechercheTransverse() throws Exception {
        String t = "sp21-recherche";
        long fermeId = ferme(t);
        creer(t, "/api/sites", ("""
                {"nom":"Rucher des tilleuls","fermeId":%d,"latitude":44.447,"longitude":1.441,
                 "dateMiseEnOeuvre":"2026-04-01","adresseRue":"12 chemin des Vignes",
                 "ville":"Figeac","pays":"FR"}""").formatted(fermeId));

        mockMvc.perform(get("/api/recherche").with(tenant(t)).param("q", "tilleul"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("site"))
                .andExpect(jsonPath("$[0].libelle").value("Rucher des tilleuls"))
                .andExpect(jsonPath("$[0].route").value("/sites"))
                // Ni position ni adresse : c'est le seul appel qui rend d'un coup
                // un echantillon de toutes les tables.
                .andExpect(content().string(Matchers.not(Matchers.containsString("44.447"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("chemin des Vignes"))));

        // La commune reste cherchable : c'est la maille a laquelle on cherche un
        // rucher, et elle est plus grossiere que l'arrondi des coordonnees.
        mockMvc.perform(get("/api/recherche").with(tenant(t)).param("q", "figeac"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Un caractere n'est pas une recherche, c'est un parcours de table.
        mockMvc.perform(get("/api/recherche").with(tenant(t)).param("q", "a"))
                .andExpect(status().isBadRequest());
    }

    // ─── Export iCalendar ────────────────────────────────────────────────────

    @Test
    @DisplayName("l'agenda .ics porte les visites planifiees, jamais leur position")
    void agendaIcs() throws Exception {
        String t = "sp21-ics";
        long fermeId = ferme(t);
        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Rucher des tilleuls","fermeId":%d,"latitude":44.447,"longitude":1.441,
                 "dateMiseEnOeuvre":"2026-04-01","adresseRue":"12 chemin des Vignes",
                 "ville":"Figeac","pays":"FR"}""").formatted(fermeId));
        long rucheId = ruche(t, siteId, fermeId);
        long agentId = agent(t);

        creer(t, "/api/plannings", ("""
                {"rucheId":%d,"agentId":%d,"datePrevue":"2026-09-07","heurePrevue":"09:30",
                 "dureeMin":45,"raison":"controle"}""").formatted(rucheId, agentId));

        String ics = mockMvc.perform(get("/api/plannings/agenda.ics").with(tenant(t))
                        .param("debut", "2026-09-01").param("fin", "2026-09-30"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(ics)
                .startsWith("BEGIN:VCALENDAR")
                .contains("BEGIN:VEVENT")
                .contains("DTSTART:20260907T093000Z")
                .contains("DTEND:20260907T101500Z")
                .contains("LOCATION:Rucher des tilleuls\\, Figeac")
                // Un .ics quitte l'application : il est synchronise chez un tiers,
                // indexe, sauvegarde. Aucune position n'y entre.
                .doesNotContain("44.447")
                .doesNotContain("chemin des Vignes")
                .doesNotContain("GEO:")
                .endsWith("END:VCALENDAR\r\n");
    }


    // ─── Miellees : la periode de floraison (V21) ────────────────────────────

    @Test
    @DisplayName("une ressource porte sa fenetre de floraison, meme a cheval sur l'annee")
    void periodeDeFloraison() throws Exception {
        String t = "sp21-miellee";
        long fermeId = ferme(t);

        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Rucher du Sud","fermeId":%d,"latitude":33.5,"longitude":9.0,
                 "dateMiseEnOeuvre":"2026-04-01",
                 "ressources":[{"ressource":"eucalyptus","distanceM":1200,
                                "moisDebut":11,"moisFin":2,"note":null},
                               {"ressource":"agrumes","distanceM":400,
                                "moisDebut":3,"moisFin":4,"note":null}]}""")
                .formatted(fermeId));

        mockMvc.perform(get("/api/sites/" + siteId).with(tenant(t)))
                .andExpect(status().isOk())
                // Triees par ressource : agrumes avant eucalyptus.
                .andExpect(jsonPath("$.ressources[0].moisDebut").value(3))
                // La fenetre qui enjambe l'annee est acceptee telle quelle : ni la
                // base ni le service ne la reordonnent, et c'est voulu.
                .andExpect(jsonPath("$.ressources[1].moisDebut").value(11))
                .andExpect(jsonPath("$.ressources[1].moisFin").value(2));
    }

    @Test
    @DisplayName("un debut de floraison sans fin est refuse avant la base")
    void floraisonIncomplete() throws Exception {
        String t = "sp21-miellee-partielle";
        long fermeId = ferme(t);

        mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"nom":"S","fermeId":%d,"latitude":45.0,"longitude":1.0,
                                 "dateMiseEnOeuvre":"2026-04-01",
                                 "ressources":[{"ressource":"thym","moisDebut":5}]}""")
                                .formatted(fermeId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Transport de transhumance (V21) ─────────────────────────────────────

    @Test
    @DisplayName("realiser un transport fait demenager le rucher, et l'historique le montre")
    void transportRealise() throws Exception {
        String t = "sp21-transport";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId);
        long agentId = agent(t);

        long transportId = creer(t, "/api/transports", ("""
                {"siteId":%d,"agentId":%d,"datePrevue":"2026-05-15","vehicule":"Camion 3,5 t",
                 "capaciteRuches":20,"nbRuches":41,"destinationLibelle":"Plateau de l'Aubrac",
                 "destinationLatitude":44.6,"destinationLongitude":3.0}""")
                .formatted(siteId, agentId));

        // 41 ruches dans un camion de 20 : trois voyages, calcules et jamais stockes.
        mockMvc.perform(get("/api/transports").with(tenant(t))
                        .param("siteId", String.valueOf(siteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].voyages").value(3))
                .andExpect(jsonPath("$[0].statut").value("prevu"));

        mockMvc.perform(post("/api/transports/" + transportId + "/realiser").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("realise"));

        // Le rucher a bouge, et l'historique porte la meme trace qu'un
        // demenagement direct : une seule regle, un seul historique.
        mockMvc.perform(get("/api/sites/" + siteId + "/emplacements").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].courant").value(true))
                .andExpect(jsonPath("$[0].motif").value("transhumance"))
                .andExpect(jsonPath("$[1].dateFin").value("2026-05-15"));

        // Un transport realise ne se realise pas deux fois.
        mockMvc.perform(post("/api/transports/" + transportId + "/realiser").with(tenant(t)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un transport sans coordonnees se planifie mais ne se realise pas")
    void transportSansCoordonnees() throws Exception {
        String t = "sp21-transport-flou";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId);
        long agentId = agent(t);

        long transportId = creer(t, "/api/transports", ("""
                {"siteId":%d,"agentId":%d,"datePrevue":"2026-05-15",
                 "destinationLibelle":"Chez le voisin, a preciser"}""")
                .formatted(siteId, agentId));

        // Ouvrir un emplacement sans position ferait un trou dans l'historique,
        // et un trou dans un historique ne se voit pas.
        mockMvc.perform(post("/api/transports/" + transportId + "/realiser").with(tenant(t)))
                .andExpect(status().isBadRequest());
    }

    // ─── Abonnement iCalendar (V21) ──────────────────────────────────────────

    @Test
    @DisplayName("un abonnement sert l'agenda de son agent sans session, et plus rien une fois revoque")
    void abonnementCalendrier() throws Exception {
        String t = "sp21-abonnement";
        long fermeId = ferme(t);
        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Rucher des tilleuls","fermeId":%d,"latitude":44.447,"longitude":1.441,
                 "dateMiseEnOeuvre":"2026-04-01","adresseRue":"12 chemin des Vignes",
                 "ville":"Figeac","pays":"FR"}""").formatted(fermeId));
        long rucheId = ruche(t, siteId, fermeId);
        long agentId = agent(t);
        long autreAgentId = creer(t, "/api/agents",
                "{\"nom\":\"Leila Haddad\",\"role\":\"superviseur\",\"email\":null}");

        creer(t, "/api/plannings", ("""
                {"rucheId":%d,"agentId":%d,"datePrevue":"%s","heurePrevue":"09:30",
                 "dureeMin":45,"raison":"controle"}""")
                .formatted(rucheId, agentId, LocalDate.now().plusDays(3)));
        creer(t, "/api/plannings", ("""
                {"rucheId":%d,"agentId":%d,"datePrevue":"%s","raison":"recolte"}""")
                .formatted(rucheId, autreAgentId, LocalDate.now().plusDays(4)));

        String creation = mockMvc.perform(post("/api/abonnements-calendrier").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"agentId":%d,"libelle":"Telephone","dureeJours":180}""")
                                .formatted(agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.derniereUtilisation").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String url = json.readTree(creation).get("url").asText();
        assertThat(url).contains("/api/calendrier/").endsWith(".ics");
        String chemin = url.substring(url.indexOf("/api/calendrier/"));

        // Le flux repond SANS jeton d'authentification : c'est la definition meme
        // d'un abonnement, et le jeton du chemin en tient lieu.
        String ics = mockMvc.perform(get(chemin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(ics)
                .startsWith("BEGIN:VCALENDAR")
                .contains("DTSTART:")
                // L'agenda d'UN agent : le planning du superviseur n'y est pas.
                .containsOnlyOnce("BEGIN:VEVENT")
                .contains("controle")
                .doesNotContain("recolte")
                // Un .ics recopie chez un tiers a chaque rafraichissement ne porte
                // pas plus de position que le telechargement.
                .doesNotContain("44.447")
                .doesNotContain("chemin des Vignes");

        // L'usage devient visible : c'est ce qui permet de reperer un jeton
        // oublie qui sert encore.
        String liste = mockMvc.perform(get("/api/abonnements-calendrier").with(tenant(t))
                        .param("agentId", String.valueOf(agentId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(liste).get(0).get("derniereUtilisation").isNull()).isFalse();
        // Le jeton lui-meme n'est jamais rendu deux fois.
        assertThat(json.readTree(liste).get(0).get("url").isNull()).isTrue();

        mockMvc.perform(delete("/api/abonnements-calendrier/"
                        + json.readTree(creation).get("id").asLong()).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));

        // Revoque : la meme URL ne repond plus. En 404 et non en 403, pour ne pas
        // confirmer qu'un jeton a existe.
        mockMvc.perform(get(chemin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("un jeton inconnu recoit le meme 404 qu'un jeton revoque")
    void jetonInconnu() throws Exception {
        mockMvc.perform(get("/api/calendrier/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA.ics"))
                .andExpect(status().isNotFound());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long ferme(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        return creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
    }

    private long site(String t) throws Exception {
        return siteDe(t, ferme(t));
    }

    private long siteDe(String t, long fermeId) throws Exception {
        return creer(t, "/api/sites",
                "{\"nom\":\"S\",\"fermeId\":" + fermeId
                        + ",\"latitude\":45.0,\"longitude\":1.0,\"dateMiseEnOeuvre\":\"2026-04-01\"}");
    }

    private long ruche(String t, long siteId, long fermeId) throws Exception {
        return creer(t, "/api/ruches",
                "{\"modele\":\"M\",\"siteId\":" + siteId + ",\"fermeId\":" + fermeId
                        + ",\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}");
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
