package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Éprouve le SPRINT-32, lot H — occupation du sol et floraison observée.
 *
 * <p>Le dernier lot du plan, et le seul terrain où un concurrent — BeeGIS — joue
 * sur le domaine que Zümm revendique. Ce qui se vérifie ici est ce qui décide de
 * la valeur des surfaces rendues :
 *
 * <ol>
 *   <li>la taxonomie est <strong>fermée</strong> : une classe inconnue fait
 *       échouer le versement ENTIER, parce qu'une couche partielle dont on
 *       ignore ce qu'elle omet rend des surfaces fausses sans le dire ;
 *   <li>l'intersection est <strong>bornée au rayon</strong> : une parcelle qui
 *       déborde ne compte pas en entier ;
 *   <li>chaque réponse porte sa <strong>source</strong> et son
 *       <strong>millésime</strong> — un pourcentage sans provenance n'engage
 *       personne ;
 *   <li>un versement <strong>remplace</strong> son millésime : verser deux fois
 *       sans purger doublerait toutes les surfaces ;
 *   <li>la floraison observée se <strong>complète</strong> au fil des semaines,
 *       et se confronte au déclaratif de la `V21`.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class EnvironnementSigIT {

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

    private JwtRequestPostProcessor apiculteur(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(new SimpleGrantedAuthority("ROLE_apiculteur"));
    }

    /**
     * Un carré de côté `cote` degrés, coin sud-ouest au rucher.
     *
     * <p>0,005° ≈ 550 m à cette latitude : le carré tient donc largement dans un
     * rayon de butinage de 3 km, et sa surface est comparable d'un test à
     * l'autre.
     */
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

    // ─── Couvert du sol ──────────────────────────────────────────────────────

    @Test
    @DisplayName("les surfaces sont rendues par classe, avec leur source et leur millesime")
    void surfacesAutourDuRucher() throws Exception {
        String t = "sp32-surfaces";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, 44.5, 1.5);

        verser(t, "RPG 2026", 2026,
                carre("culture", 44.5, 1.5, 0.005),
                carre("foret", 44.51, 1.51, 0.005));

        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/couvert").with(tenant(t)))
                .andExpect(status().isOk())
                // Un pourcentage sans sa provenance n'engage personne : les deux
                // accompagnent les surfaces sans exception (§13).
                .andExpect(jsonPath("$.source").value("RPG 2026"))
                .andExpect(jsonPath("$.millesime").value(2026))
                .andExpect(jsonPath("$.rayonKm").value(3.0))
                .andExpect(jsonPath("$.surfaces.length()").value(2))
                .andExpect(jsonPath("$.surfaces[?(@.classe == 'culture')].surfaceHa")
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.greaterThan(20.0))))
                // La part du cercle effectivement DECRITE par la couche : 30 %
                // de couverture et 70 % de silence ne disent pas « 70 % de sol nu ».
                .andExpect(jsonPath("$.couverte").exists());
    }

    @Test
    @DisplayName("une classe hors taxonomie fait echouer le versement entier")
    void taxonomieFermee() throws Exception {
        String t = "sp32-taxonomie";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, 44.5, 1.5);

        // Accepter les entites valides et rejeter les autres laisserait une
        // couche partielle dont personne ne sait ce qu'elle omet — et les
        // surfaces calculees dessus seraient fausses sans le dire.
        mockMvc.perform(post("/api/environnement/couvert").with(tenant(t))
                        .param("source", "OSM").param("millesime", "2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":["
                                + carre("culture", 44.5, 1.5, 0.005) + ","
                                + carre("landuse=meadow", 44.51, 1.51, 0.005) + "]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/couvert").with(tenant(t)))
                .andExpect(jsonPath("$.surfaces.length()").value(0));
    }

    @Test
    @DisplayName("verser un millesime le remplace, il ne s'y ajoute pas")
    void versementRemplace() throws Exception {
        String t = "sp32-remplace";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, 44.5, 1.5);

        verser(t, "RPG 2026", 2026, carre("culture", 44.5, 1.5, 0.005));
        String premier = mockMvc.perform(
                        get("/api/environnement/sites/" + siteId + "/couvert").with(tenant(t)))
                .andReturn().getResponse().getContentAsString();
        double avant = json.readTree(premier).get("surfaces").get(0).get("surfaceHa")
                .asDouble();

        // Verser deux fois sans purger doublerait toutes les surfaces, et le
        // total depasserait celui du cercle sans que rien ne le signale.
        verser(t, "RPG 2026", 2026, carre("culture", 44.5, 1.5, 0.005));

        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/couvert").with(tenant(t)))
                .andExpect(jsonPath("$.surfaces[0].surfaceHa").value(avant));
    }

    @Test
    @DisplayName("la rotation se lit en comparant deux millesimes")
    void rotationDesCultures() throws Exception {
        String t = "sp32-rotation";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, 44.5, 1.5);

        verser(t, "RPG 2025", 2025, carre("culture", 44.5, 1.5, 0.005));
        verser(t, "RPG 2026", 2026, carre("prairie", 44.5, 1.5, 0.005));

        // La rotation ne se DEDUIT pas d'une couche : elle se LIT. C'est pour
        // cela que le millesime est obligatoire des la premiere ligne versee.
        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/rotation").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].millesime").value(2026))
                .andExpect(jsonPath("$[0].surfaces[0].classe").value("prairie"))
                .andExpect(jsonPath("$[1].millesime").value(2025))
                .andExpect(jsonPath("$[1].surfaces[0].classe").value("culture"));
    }

    @Test
    @DisplayName("sans couche versee, l'API le dit au lieu de rendre des zeros")
    void aucuneCouche() throws Exception {
        String t = "sp32-vide";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, 44.5, 1.5);

        // Des surfaces nulles se liraient comme un environnement vide. Rien
        // n'arrive tout seul, et c'est le cout assume de ne rien aller chercher
        // dehors (ADR-015).
        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/couvert").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.millesime").doesNotExist())
                .andExpect(jsonPath("$.surfaces.length()").value(0))
                .andExpect(jsonPath("$.rayonKm").value(3.0));
    }

    @Test
    @DisplayName("le rayon de butinage du rucher l'emporte sur le defaut")
    void rayonParRucher() throws Exception {
        String t = "sp32-rayon";
        long fermeId = ferme(t);
        long siteId = creer(t, "/api/sites", ("""
                {"nom":"Montagne","fermeId":%d,"latitude":44.5,"longitude":1.5,
                 "dateMiseEnOeuvre":"2026-04-01","rayonButinageKm":5.5}""").formatted(fermeId));

        // Le figer reviendrait a dessiner le meme cercle partout, et a calculer
        // les surfaces sur ce cercle-la.
        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/couvert").with(tenant(t)))
                .andExpect(jsonPath("$.rayonKm").value(5.5));
    }

    @Test
    @DisplayName("verser une couche est reserve au pilotage, la lire ne l'est pas")
    void versementReserveAuPilotage() throws Exception {
        String t = "sp32-rbac";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId, 44.5, 1.5);

        mockMvc.perform(get("/api/environnement/sites/" + siteId + "/couvert")
                        .with(apiculteur(t)))
                .andExpect(status().isOk());

        // Verser engage toutes les surfaces calculees ensuite ; purger efface une
        // donnee que personne ne reversera de memoire.
        mockMvc.perform(post("/api/environnement/couvert").with(apiculteur(t))
                        .param("source", "OSM").param("millesime", "2026")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FeatureCollection\",\"features\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/environnement/couvert").with(apiculteur(t))
                        .param("millesime", "2026"))
                .andExpect(status().isForbidden());
    }

    // ─── Floraison observee ──────────────────────────────────────────────────

    @Test
    @DisplayName("la floraison observee se complete, et se confronte au declaratif")
    void floraisonObservee() throws Exception {
        String t = "sp32-floraison";
        long fermeId = ferme(t);
        // Les ressources florales se declarent AVEC le rucher (SPRINT-21) : il
        // n'y a pas de sous-ressource, la liste remplace celle du site.
        long[] ids = siteAvecRessource(t, fermeId,
                "{\"ressource\":\"colza\",\"distanceM\":800,\"moisDebut\":4,\"moisFin\":5}");
        long siteId = ids[0];
        long ressourceId = ids[1];

        creer(t, "/api/environnement/floraisons", ("""
                {"ressourceId":%d,"annee":2026,"dateDebut":"2026-04-18"}""")
                .formatted(ressourceId));

        // L'apiculteur note le debut en avril et complete le pic en mai :
        // refuser la seconde saisie lui ferait perdre la premiere.
        mockMvc.perform(post("/api/environnement/floraisons").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"ressourceId":%d,"annee":2026,"dateDebut":"2026-04-18",
                                 "datePic":"2026-05-02","abondance":2}""")
                                .formatted(ressourceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.datePic").value("2026-05-02"))
                // L'ecart au declaratif : dix-sept jours apres le 1er avril.
                // C'est la seule chose qui permette de dire « cette annee,
                // c'etait en retard ».
                .andExpect(jsonPath("$.moisDeclare").value(4))
                .andExpect(jsonPath("$.ecartJours").value(17));

        mockMvc.perform(get("/api/environnement/floraisons").with(tenant(t))
                        .param("siteId", String.valueOf(siteId)))
                // UNE observation, pas deux : une ressource ne fleurit qu'une
                // fois par an.
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("une floraison qui finit avant de commencer est refusee en 400")
    void floraisonIncoherente() throws Exception {
        String t = "sp32-ordre";
        long fermeId = ferme(t);
        long ressourceId = siteAvecRessource(t, fermeId,
                "{\"ressource\":\"tilleul\",\"distanceM\":300}")[1];

        // La base le refuserait aussi, mais en 409 : deux dates inversees sont
        // une faute de frappe, pas un conflit d'etat.
        mockMvc.perform(post("/api/environnement/floraisons").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"ressourceId":%d,"annee":2026,"dateDebut":"2026-06-10",
                                 "dateFin":"2026-06-01"}""").formatted(ressourceId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private long ferme(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        return creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
    }

    /**
     * Un rucher avec UNE ressource florale declaree.
     *
     * @return l'identifiant du site, puis celui de la ressource
     */
    private long[] siteAvecRessource(String t, long fermeId, String ressource) throws Exception {
        String corps = ("{\"nom\":\"S\",\"fermeId\":%d,\"latitude\":44.5,"
                + "\"longitude\":1.5,\"dateMiseEnOeuvre\":\"2026-04-01\","
                + "\"ressources\":[%s]}").formatted(fermeId, ressource);
        String rep = mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var noeud = json.readTree(rep);
        return new long[] {noeud.get("id").asLong(),
                noeud.get("ressources").get(0).get("id").asLong()};
    }

    private long site(String t, long fermeId, double lat, double lon) throws Exception {
        return creer(t, "/api/sites", ("""
                {"nom":"S","fermeId":%d,"latitude":%s,"longitude":%s,
                 "dateMiseEnOeuvre":"2026-04-01"}""").formatted(fermeId, lat, lon));
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
