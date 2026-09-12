package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Éprouve le SPRINT-33, lot K — vérification terrain, zones traitées déclarées
 * et feuille de chargement.
 *
 * <p>Les trois dernières lignes de travail du document d'écart. Ce qui se
 * vérifie ici est, à chaque fois, la <strong>borne</strong> qui empêche la
 * fonction de promettre plus qu'elle ne tient :
 *
 * <ol>
 *   <li>le constat terrain <strong>ne remplace pas</strong> la classe de la
 *       source — la parcelle garde les deux, sans quoi personne ne pourrait plus
 *       dire de quel millésime se défier — mais les surfaces, elles, comptent le
 *       constat ;
 *   <li>une exposition sans déclaration rend <strong>zéro et {@code null}</strong>,
 *       jamais « aucun traitement » : une couche déclarative est incomplète par
 *       construction ;
 *   <li>la feuille de chargement <strong>nomme</strong> le manque et ne le
 *       corrige pas ;
 *   <li>et le cloisonnement multi-tenant tient sur les deux tables neuves, qui
 *       sont en SQL natif — donc hors du discriminant Hibernate.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class TerrainVerifieIT {

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
                        new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    // ─── Ground truthing ────────────────────────────────────────────────────

    @Test
    @DisplayName("le constat terrain s'ajoute a la source, il ne l'ecrase pas")
    void constatNEcrasePasLaSource() throws Exception {
        String t = "sp33-constat";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        verser(t, "RPG 2026", 2026, carre("culture", 44.5, 1.5, 0.005));

        long parcelle = premiereParcelle(t);
        mockMvc.perform(post("/api/environnement/couvert/parcelles/" + parcelle + "/constat")
                        .with(tenant(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"classeConstatee":"prairie","constateLe":"2026-05-02",
                                 "note":"colza retourne, seme en prairie"}"""))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/environnement/couvert/parcelles")
                        .with(tenant(t)).param("enAttente", "false"))
                .andExpect(status().isOk())
                // Les DEUX subsistent : effacer « culture » ferait raconter a la
                // parcelle que le RPG avait raison depuis le debut, et la
                // fiabilite d'un millesime ne se mesurerait plus.
                .andExpect(jsonPath("$[0].classe").value("culture"))
                .andExpect(jsonPath("$[0].classeConstatee").value("prairie"))
                .andExpect(jsonPath("$[0].constateLe").value("2026-05-02"));

        // …mais les surfaces comptent le CONSTAT : le terrain fait foi des qu'il
        // a parle, sans quoi le ground truthing ne servirait a rien.
        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/couvert").with(tenant(t)))
                .andExpect(jsonPath("$.surfaces[0].classe").value("prairie"));
    }

    @Test
    @DisplayName("marquer une parcelle engendre UNE tache par rucher, pas une par parcelle")
    void tacheDeVerification() throws Exception {
        String t = "sp33-verif";
        long fermeId = ferme(t);
        site(t, fermeId);
        verser(t, "RPG 2026", 2026,
                carre("culture", 44.5, 1.5, 0.003),
                carre("culture", 44.504, 1.504, 0.003),
                carre("culture", 44.508, 1.508, 0.003));

        for (long parcelle : parcelles(t)) {
            mockMvc.perform(post("/api/environnement/couvert/parcelles/" + parcelle
                            + "/a-confirmer").with(tenant(t)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(post("/api/regles/executer").with(tenant(t)))
                .andExpect(status().isOk());

        // Trois parcelles, UNE tache : quarante taches « verifier la parcelle
        // 17 843 » rendraient la liste illisible en une matinee.
        mockMvc.perform(get("/api/taches").with(tenant(t)))
                .andExpect(jsonPath("$[?(@.regleCode == 'verification-couvert')]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[?(@.regleCode == 'verification-couvert')].libelle")
                        .value(org.hamcrest.Matchers.contains(
                                org.hamcrest.Matchers.containsString("3 parcelle(s)"))));
    }

    @Test
    @DisplayName("le constat leve le doute : la regle ne repropose plus la parcelle")
    void constatLeveLeDoute() throws Exception {
        String t = "sp33-doute";
        long fermeId = ferme(t);
        site(t, fermeId);
        verser(t, "RPG 2026", 2026, carre("culture", 44.5, 1.5, 0.005));
        long parcelle = premiereParcelle(t);

        mockMvc.perform(post("/api/environnement/couvert/parcelles/" + parcelle
                        + "/a-confirmer").with(tenant(t)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/environnement/couvert/parcelles").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/environnement/couvert/parcelles/" + parcelle + "/constat")
                        .with(tenant(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classeConstatee\":\"culture\",\"constateLe\":\"2026-05-02\"}"))
                .andExpect(status().isNoContent());

        // Sans cette retombee, la regle reproposerait la meme tournee chaque
        // annee sur une parcelle deja tranchee.
        mockMvc.perform(get("/api/environnement/couvert/parcelles").with(tenant(t)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("la fiabilite compte les dementis, elle ne calcule aucun taux")
    void fiabiliteEnNombres() throws Exception {
        String t = "sp33-fiabilite";
        long fermeId = ferme(t);
        site(t, fermeId);
        verser(t, "RPG 2026", 2026,
                carre("culture", 44.5, 1.5, 0.003),
                carre("culture", 44.504, 1.504, 0.003));

        long[] ids = parcelles(t);
        constater(t, ids[0], "prairie");   // dementie
        constater(t, ids[1], "culture");   // confirmee

        mockMvc.perform(get("/api/environnement/couvert/fiabilite")
                        .with(tenant(t)).param("millesime", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelles").value(2))
                .andExpect(jsonPath("$.verifiees").value(2))
                // Un « taux d'exactitude de 50 % » sur deux visites serait lu
                // comme un verdict sur la couche entiere. Les nombres bruts
                // portent leur propre reserve.
                .andExpect(jsonPath("$.dementies").value(1))
                .andExpect(jsonPath("$.enAttente").value(0));
    }

    @Test
    @DisplayName("un constat anterieur au millesime est refuse : il ne verifie rien")
    void constatAnterieurRefuse() throws Exception {
        String t = "sp33-anterieur";
        long fermeId = ferme(t);
        site(t, fermeId);
        verser(t, "RPG 2026", 2026, carre("culture", 44.5, 1.5, 0.005));

        mockMvc.perform(post("/api/environnement/couvert/parcelles/" + premiereParcelle(t)
                        + "/constat").with(tenant(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classeConstatee\":\"prairie\",\"constateLe\":\"2024-05-02\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une classe de constat hors taxonomie est refusee")
    void constatHorsTaxonomie() throws Exception {
        String t = "sp33-taxo";
        long fermeId = ferme(t);
        site(t, fermeId);
        verser(t, "RPG 2026", 2026, carre("culture", 44.5, 1.5, 0.005));

        // Le terrain CORRIGE la source ; il n'invente pas une classe de plus,
        // sans quoi deux exploitations redeviendraient incomparables.
        mockMvc.perform(post("/api/environnement/couvert/parcelles/" + premiereParcelle(t)
                        + "/constat").with(tenant(t)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classeConstatee\":\"tournesol\",\"constateLe\":\"2026-05-02\"}"))
                .andExpect(status().isBadRequest());
    }

    // ─── Zones traitées déclarées ───────────────────────────────────────────

    @Test
    @DisplayName("aucune declaration : zero et null, jamais « aucun traitement »")
    void silenceNEstPasGarantie() throws Exception {
        String t = "sp33-silence";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);

        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/exposition")
                        .with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declarations").value(0))
                // C'est CETTE date nulle qui distingue « rien ne m'a ete declare »
                // de « rien n'a ete epandu ».
                .andExpect(jsonPath("$.derniereDeclaration").doesNotExist())
                .andExpect(jsonPath("$.distanceMinM").doesNotExist())
                .andExpect(jsonPath("$.sousDelaiRentree").value(0));
    }

    @Test
    @DisplayName("une zone declaree remonte dans l'exposition du rucher, avec sa date")
    void expositionDeclaree() throws Exception {
        String t = "sp33-expo";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        LocalDate hier = LocalDate.now().minusDays(1);

        declarer(t, hier, 48);

        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/exposition")
                        .with(tenant(t)))
                .andExpect(jsonPath("$.declarations").value(1))
                .andExpect(jsonPath("$.derniereDeclaration").value(hier.toString()))
                .andExpect(jsonPath("$.sousDelaiRentree").value(1))
                // Aucune identite de tiers dans la reponse : le §13 refuse un
                // annuaire de voisins, et le modele n'en porte pas la trace.
                .andExpect(jsonPath("$.zones[0].substance").value("cypermethrine"))
                .andExpect(jsonPath("$.zones[0].origine").value("voisin_declare"));
    }

    @Test
    @DisplayName("une declaration recente engendre une tache de vigilance qui ne conclut pas")
    void tacheDeVigilance() throws Exception {
        String t = "sp33-vigilance";
        long fermeId = ferme(t);
        site(t, fermeId);
        declarer(t, LocalDate.now().minusDays(2), 24);

        mockMvc.perform(post("/api/regles/executer").with(tenant(t)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/taches").with(tenant(t)))
                .andExpect(jsonPath("$[?(@.regleCode == 'zone-traitee-proche')].priorite")
                        .value(org.hamcrest.Matchers.contains("haute")));
    }

    @Test
    @DisplayName("une declaration datee dans le futur est refusee")
    void declarationFutureRefusee() throws Exception {
        String t = "sp33-futur";
        mockMvc.perform(post("/api/environnement/zones-traitees").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsZone(LocalDate.now().plusDays(3), 24)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("les zones traitees sont cloisonnees par exploitation")
    void cloisonnementDesZones() throws Exception {
        declarer("sp33-tenant-a", LocalDate.now().minusDays(1), 24);

        // La table est en SQL natif : elle echappe au discriminant Hibernate, et
        // c'est le filtre `tenant_id` explicite du depot qui tient — la RLS ne
        // protegerait pas si l'application se connectait en superutilisateur.
        mockMvc.perform(get("/api/environnement/zones-traitees").with(tenant("sp33-tenant-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("supprimer une zone inconnue rend 404")
    void suppressionInconnue() throws Exception {
        mockMvc.perform(delete("/api/environnement/zones-traitees/999999")
                        .with(tenant("sp33-suppr")))
                .andExpect(status().isNotFound());
    }

    // ─── Feuille de chargement ──────────────────────────────────────────────

    @Test
    @DisplayName("la feuille consolide les besoins de la tournee et NOMME le manque")
    void feuilleDeChargement() throws Exception {
        String t = "sp33-chargement";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        long rucheId = ruche(t, fermeId, siteId);
        long agentId = creer(t, "/api/agents", "{\"nom\":\"Nadia\",\"role\":\"apiculteur\"}");
        LocalDate jour = LocalDate.now();
        creer(t, "/api/plannings", ("""
                {"rucheId":%d,"agentId":%d,"datePrevue":"%s","raison":"controle"}""")
                .formatted(rucheId, agentId, jour));
        long candi = creer(t, "/api/consommables", """
                {"libelle":"Candi","categorie":"candi","quantite":5,"unite":"kg",
                 "seuilAlerte":1}""");
        creer(t, "/api/taches", ("""
                {"libelle":"Nourrir la 12","rucheId":%d,"echeance":"%s",
                 "consommableId":%d,"quantitePrevue":9}""")
                .formatted(rucheId, jour, candi));

        mockMvc.perform(get("/api/plannings/chargement").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", jour.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapes.length()").value(1))
                .andExpect(jsonPath("$.etapes[0].taches[0]").value("Nourrir la 12"))
                .andExpect(jsonPath("$.besoins[0].requis").value(9.0))
                .andExpect(jsonPath("$.besoins[0].enStock").value(5.0))
                // La feuille signale et s'arrete la : decider quelle ruche sauter
                // est une decision d'exploitation.
                .andExpect(jsonPath("$.besoins[0].suffisant").value(false))
                .andExpect(jsonPath("$.manquants").value(1));
    }

    @Test
    @DisplayName("un besoin non chiffre ne declare aucun manque")
    void besoinNonChiffre() throws Exception {
        String t = "sp33-nonchiffre";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        long rucheId = ruche(t, fermeId, siteId);
        long agentId = creer(t, "/api/agents", "{\"nom\":\"Sami\",\"role\":\"apiculteur\"}");
        LocalDate jour = LocalDate.now();
        creer(t, "/api/plannings", ("""
                {"rucheId":%d,"agentId":%d,"datePrevue":"%s","raison":"controle"}""")
                .formatted(rucheId, agentId, jour));
        long candi = creer(t, "/api/consommables", """
                {"libelle":"Candi","categorie":"candi","quantite":0,"unite":"kg",
                 "seuilAlerte":1}""");
        creer(t, "/api/taches", ("""
                {"libelle":"Prendre du candi","rucheId":%d,"echeance":"%s",
                 "consommableId":%d}""").formatted(rucheId, jour, candi));

        mockMvc.perform(get("/api/plannings/chargement").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", jour.toString()))
                // « 0 kg de candi » ferait partir sans. On ne peut pas manquer
                // d'une quantite qu'on n'a pas exprimee.
                .andExpect(jsonPath("$.besoins[0].requis").doesNotExist())
                .andExpect(jsonPath("$.besoins[0].suffisant").value(true))
                .andExpect(jsonPath("$.manquants").value(0));
    }

    @Test
    @DisplayName("retirer le consommable d'une tache retire aussi sa quantite")
    void quantiteSuitLeConsommable() throws Exception {
        String t = "sp33-quantite";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        long rucheId = ruche(t, fermeId, siteId);
        long candi = creer(t, "/api/consommables", """
                {"libelle":"Candi","categorie":"candi","quantite":10,"unite":"kg",
                 "seuilAlerte":1}""");
        long tache = creer(t, "/api/taches", ("""
                {"libelle":"Nourrir","rucheId":%d,"consommableId":%d,"quantitePrevue":4}""")
                .formatted(rucheId, candi));

        // Garder la quantite alors que le consommable vient d'etre retire
        // laisserait un nombre sans unite ni objet, que `ck_tache_quantite`
        // refuse.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/taches/" + tache).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"libelle\":\"Nourrir\",\"quantitePrevue\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consommableId").doesNotExist())
                .andExpect(jsonPath("$.quantitePrevue").doesNotExist());
    }

    // ─── fixtures ───────────────────────────────────────────────────────────

    private String carre(String classe, double lat, double lon, double cote) {
        return ("""
                {"type":"Feature","properties":{"classe":"%s"},
                 "geometry":{"type":"Polygon","coordinates":[[
                   [%s,%s],[%s,%s],[%s,%s],[%s,%s],[%s,%s]]]}}""")
                .formatted(classe, lon, lat, lon + cote, lat, lon + cote, lat + cote,
                        lon, lat + cote, lon, lat);
    }

    private void verser(String t, String source, int millesime, String... entites)
            throws Exception {
        mockMvc.perform(post("/api/environnement/couvert").with(tenant(t))
                        .param("source", source).param("millesime", String.valueOf(millesime))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":["
                                + String.join(",", entites) + "]}"))
                .andExpect(status().isOk());
    }

    private long[] parcelles(String t) throws Exception {
        String rep = mockMvc.perform(get("/api/environnement/couvert/parcelles")
                        .with(tenant(t)).param("enAttente", "false"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var noeud = json.readTree(rep);
        long[] ids = new long[noeud.size()];
        for (int i = 0; i < noeud.size(); i++) {
            ids[i] = noeud.get(i).get("id").asLong();
        }
        return ids;
    }

    private long premiereParcelle(String t) throws Exception {
        return parcelles(t)[0];
    }

    private void constater(String t, long parcelle, String classe) throws Exception {
        mockMvc.perform(post("/api/environnement/couvert/parcelles/" + parcelle + "/constat")
                        .with(tenant(t)).contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"classeConstatee\":\"%s\",\"constateLe\":\"2026-05-02\"}")
                                .formatted(classe)))
                .andExpect(status().isNoContent());
    }

    private String corpsZone(LocalDate date, Integer delai) {
        return ("""
                {"geometrie":{"type":"Polygon","coordinates":[[
                   [1.5,44.5],[1.51,44.5],[1.51,44.51],[1.5,44.51],[1.5,44.5]]]},
                 "dateTraitement":"%s","substance":"cypermethrine",
                 "origine":"voisin_declare","delaiRentreeH":%s}""")
                .formatted(date, delai);
    }

    private void declarer(String t, LocalDate date, Integer delai) throws Exception {
        mockMvc.perform(post("/api/environnement/zones-traitees").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corpsZone(date, delai)))
                .andExpect(status().isCreated());
    }

    private long ferme(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        return creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
    }

    private long site(String t, long fermeId) throws Exception {
        return creer(t, "/api/sites", ("""
                {"nom":"Colline","fermeId":%d,"latitude":44.5,"longitude":1.5,
                 "dateMiseEnOeuvre":"2026-04-01"}""").formatted(fermeId));
    }

    private long ruche(String t, long fermeId, long siteId) throws Exception {
        return creer(t, "/api/ruches", ("""
                {"modele":"Dadant","siteId":%d,"fermeId":%d,
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
