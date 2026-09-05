package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Éprouve le SPRINT-28, lot I — le carnet paramétrable.
 *
 * <p>Ce qui mérite un test d'intégration ici est précisément ce qui rend le
 * paramétrage inoffensif pour la statistique :
 *
 * <ol>
 *   <li>le référentiel est <strong>fermé</strong> : un gabarit ne peut retenir
 *       qu'un point qui existe déjà ;
 *   <li>la valeur doit correspondre au <strong>type</strong> du point — c'est la
 *       seule règle que la base ne peut pas tenir, le type vivant dans une autre
 *       table ;
 *   <li>l'absence de relevé n'est pas un « non » : elle ne remonte pas dans les
 *       comptes ;
 *   <li>un seul gabarit par défaut, et la bascule est possible ;
 *   <li>une ordonnance datée sans référence est refusée par la base ;
 *   <li>le réfractomètre refuse tout ce qui sort de la table plutôt que
 *       d'extrapoler.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CarnetParametrableIT {

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

    // ─── Le référentiel, et sa fermeture ─────────────────────────────────────

    @Test
    @DisplayName("le référentiel est servi trié, et un gabarit ne peut pas inventer un point")
    void referentielFerme() throws Exception {
        String t = "sp28-referentiel";

        mockMvc.perform(get("/api/carnet/points").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("pop_forte"))
                .andExpect(jsonPath("$[0].typeValeur").value("booleen"))
                // Les points d'échelle existent : un oui/non perdrait
                // l'intensité, qui est justement ce qu'on observe.
                .andExpect(jsonPath("$[?(@.code == 'propolisation')].typeValeur")
                        .value("echelle"));

        // Un code absent du référentiel est refusé en 400 : la clé étrangère le
        // refuserait aussi, mais en 500 — or c'est une faute de frappe du
        // client, pas une panne du serveur.
        mockMvc.perform(post("/api/carnet/gabarits").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Inventif","points":["ce_point_nexiste_pas"]}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un apiculteur lit le carnet mais n'en change pas la forme")
    void formeReserveeAuPilotage() throws Exception {
        String t = "sp28-rbac";

        mockMvc.perform(get("/api/carnet/points").with(apiculteur(t)))
                .andExpect(status().isOk());

        // Quelles cases figurent à la saisie engage toutes les inspections à
        // venir : c'est une décision d'exploitation, pas un réglage personnel.
        mockMvc.perform(post("/api/carnet/gabarits").with(apiculteur(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Le mien","points":[]}"""))
                .andExpect(status().isForbidden());
    }

    // ─── Gabarits ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un seul gabarit par défaut : le second bascule le premier")
    void unSeulGabaritParDefaut() throws Exception {
        String t = "sp28-defaut";

        long premier = creer(t, "/api/carnet/gabarits", """
                {"nom":"Printemps","parDefaut":true,
                 "points":["pop_forte","essaimage_signes"]}""");
        long second = creer(t, "/api/carnet/gabarits", """
                {"nom":"Hivernage","parDefaut":true,"noyauCadres":false,
                 "points":["reserves_miel_suffisantes"]}""");

        mockMvc.perform(get("/api/carnet/gabarits/" + premier).with(tenant(t)))
                .andExpect(jsonPath("$.parDefaut").value(false))
                // La bascule ne touche à rien d'autre : les points du premier
                // gabarit restent les siens.
                .andExpect(jsonPath("$.points.length()").value(2));
        mockMvc.perform(get("/api/carnet/gabarits/" + second).with(tenant(t)))
                .andExpect(jsonPath("$.parDefaut").value(true))
                // Le noyau s'éteint section par section ; il ne se déplace pas.
                .andExpect(jsonPath("$.noyauCadres").value(false))
                .andExpect(jsonPath("$.noyauCouvain").value(true));
    }

    @Test
    @DisplayName("la liste des points se remplace, elle ne fusionne pas")
    void remplacementDesPoints() throws Exception {
        String t = "sp28-remplacement";
        long id = creer(t, "/api/carnet/gabarits", """
                {"nom":"Complet","points":["pop_forte","pop_faible","propolisation"]}""");

        // Un carnet dont on ne peut qu'ajouter des cases finit par n'être plus
        // rempli : retirer doit être aussi simple qu'ajouter.
        mockMvc.perform(put("/api/carnet/gabarits/" + id).with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nom":"Complet","points":["propolisation"]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0]").value("propolisation"));
    }

    // ─── Relevés ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la valeur doit correspondre au type du point, et le service le refuse")
    void valeurDuBonType() throws Exception {
        String t = "sp28-types";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);
        long agentId = agent(t);

        // Une intensité sur une case à cocher ne veut rien dire. La base ne peut
        // pas le voir — le type vit dans une autre table, hors de portée d'un
        // CHECK ; c'est le service qui tient la règle.
        mockMvc.perform(post("/api/visites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-02",
                                 "points":[{"code":"pop_forte","niveau":2}]}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());

        // Et l'inverse : une case cochée sur un point d'échelle.
        mockMvc.perform(post("/api/visites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-02",
                                 "points":[{"code":"propolisation","coche":true}]}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ne pas cocher n'est pas cocher « non » : le point absent n'est pas relevé")
    void absenceDeReleveNestPasUnNon() throws Exception {
        String t = "sp28-absence";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);
        long agentId = agent(t);

        String reponse = mockMvc.perform(post("/api/visites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-03",
                                 "points":[{"code":"pop_forte","coche":true},
                                           {"code":"pop_faible","coche":false},
                                           {"code":"propolisation","niveau":2}]}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.points.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        long visiteId = json.readTree(reponse).get("id").asLong();

        // Relecture : trois points relevés, et rien d'autre. Les quarante et un
        // points non envoyés n'ont pas été REGARDÉS — les compter comme des
        // « non » rendrait toute statistique fausse dans le sens rassurant.
        mockMvc.perform(get("/api/visites/" + visiteId).with(tenant(t)))
                .andExpect(jsonPath("$.points.length()").value(3))
                .andExpect(jsonPath("$.points[?(@.code == 'pop_faible')].coche").value(false))
                .andExpect(jsonPath("$.points[?(@.code == 'propolisation')].niveau").value(2))
                .andExpect(jsonPath("$.points[?(@.code == 'varroas_visibles')]").isEmpty());
    }

    @Test
    @DisplayName("les statistiques comptent les relevés, pas les visites")
    void statistiquesSurLesRelevesSeulement() throws Exception {
        String t = "sp28-stats";
        long fermeId = ferme(t);
        long siteId = site(t, fermeId);
        long rucheId = ruche(t, siteId, fermeId);
        long agentId = agent(t);

        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-06-01",
                 "points":[{"code":"pop_forte","coche":true},
                           {"code":"varroas_visibles","niveau":1}]}""")
                .formatted(rucheId, agentId));
        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-06-08",
                 "points":[{"code":"pop_forte","coche":false},
                           {"code":"varroas_visibles","niveau":3}]}""")
                .formatted(rucheId, agentId));
        // Une visite éclair, sans grille : elle ne doit peser sur aucun taux.
        creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-06-15"}""")
                .formatted(rucheId, agentId));

        mockMvc.perform(get("/api/carnet/statistiques").with(tenant(t))
                        .param("depuis", "2026-05-01").param("jusqu", "2026-07-01"))
                .andExpect(status().isOk())
                // Deux relevés sur trois visites : la troisième n'a pas regardé.
                .andExpect(jsonPath("$[?(@.code == 'pop_forte')].releves").value(2))
                .andExpect(jsonPath("$[?(@.code == 'pop_forte')].presents").value(1))
                // Une moyenne d'échelle, jamais de moyenne de cases cochées.
                .andExpect(jsonPath("$[?(@.code == 'varroas_visibles')].moyenneEchelle")
                        .value(2.0))
                // Aucune moyenne sur un point booleen : le filtre ne ramene rien.
                .andExpect(jsonPath("$[?(@.code == 'pop_forte' && @.moyenneEchelle != null)]")
                        .isEmpty());

        // Hors fenêtre, rien : la borne est ce qui empêche une requête de
        // balayer toute l'histoire de l'exploitation.
        mockMvc.perform(get("/api/carnet/statistiques").with(tenant(t))
                        .param("depuis", "2025-01-01").param("jusqu", "2025-12-31"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── Ordonnances vétérinaires ────────────────────────────────────────────

    @Test
    @DisplayName("une ordonnance datée sans référence est refusée ; le scan s'y attache")
    void ordonnanceVerifiable() throws Exception {
        String t = "sp28-ordonnance";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);
        long agentId = agent(t);

        // Une date d'ordonnance sans ordonnance ne désigne rien.
        mockMvc.perform(post("/api/traitements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                                 "dateDebut":"2026-08-01","ordonnanceVeterinaire":"Dr Martin",
                                 "ordonnanceDate":"2026-07-30"}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isConflict());

        long traitementId = creer(t, "/api/traitements", ("""
                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                 "dateDebut":"2026-08-01","ordonnance":"ORD-2026-114",
                 "ordonnanceVeterinaire":"Dr Martin","ordonnanceDate":"2026-07-30"}""")
                .formatted(rucheId, agentId));

        // Le registre devient vérifiable quand la pièce y est attachée, et non
        // seulement référencée : sixième cible de `Photo` (SPRINT-28).
        mockMvc.perform(post("/api/photos").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"cible":"TRAITEMENT","cibleId":%d,
                                 "url":"https://exemple.test/ord-114.jpg",
                                 "legende":"Ordonnance du 30/07"}""").formatted(traitementId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/photos").with(tenant(t))
                        .param("cible", "TRAITEMENT").param("cibleId", String.valueOf(traitementId)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("le référentiel de produits pré-remplit, et dit que la notice fait foi")
    void referentielDeProduits() throws Exception {
        String t = "sp28-produits";

        mockMvc.perform(get("/api/carnet/produits").with(tenant(t)))
                .andExpect(status().isOk())
                // Un délai de zéro jour, lu seul, se comprend comme « on peut
                // récolter ». La contrainte réelle est ailleurs, et elle est
                // portée par une colonne dédiée.
                .andExpect(jsonPath("$[?(@.code == 'apivar')].haussesRetirees").value(true))
                .andExpect(jsonPath("$[?(@.code == 'apivar')].substanceActive").value("amitraze"))
                .andExpect(jsonPath("$[?(@.code == 'apivar')].mention").isNotEmpty());
    }

    // ─── Réfractomètre ───────────────────────────────────────────────────────

    @Test
    @DisplayName("le réfractomètre convertit, corrige la température, et refuse d'extrapoler")
    void refractometre() throws Exception {
        String t = "sp28-refracto";

        // Ancre de la table : 1,4915 à 20 °C, soit très exactement 18 % d'eau —
        // le seuil au-delà duquel un miel fermente en pot.
        mockMvc.perform(get("/api/calculateurs/refractometre").with(tenant(t))
                        .param("indice", "1.4915"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.humiditePct").value(18.0))
                .andExpect(jsonPath("$.verdict").value("stable"));

        // La même lecture faite à 10 °C décrit un miel plus HUMIDE : l'indice
        // monte quand la température baisse, et négliger la correction ferait
        // passer pour stable un miel qui fermentera.
        mockMvc.perform(get("/api/calculateurs/refractometre").with(tenant(t))
                        .param("indice", "1.4915").param("temperatureC", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.humiditePct").value(18.9))
                .andExpect(jsonPath("$.verdict").value("risque_fermentation"));

        // Hors de la table publiée, rien n'est rendu : un chiffre extrapolé sur
        // cette mesure-là serait cru.
        mockMvc.perform(get("/api/calculateurs/refractometre").with(tenant(t))
                        .param("indice", "1.4700"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("le taux d'humidité est réservé au miel")
    void humiditeReserveeAuMiel() throws Exception {
        String t = "sp28-humidite";
        long fermeId = ferme(t);
        long rucheId = ruche(t, site(t, fermeId), fermeId);

        creer(t, "/api/recoltes", ("""
                {"rucheId":%d,"dateRecolte":"2026-07-20","quantiteKg":18,"humiditePct":17.2}""")
                .formatted(rucheId));

        // Un taux d'eau sur de la cire ne veut rien dire, et la base le refuse
        // plutôt que de laisser produire un tableau de bord incohérent.
        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"dateRecolte":"2026-07-21","quantiteKg":2,
                                 "typeProduit":"cire","unite":"kg","humiditePct":17.2}""")
                                .formatted(rucheId)))
                .andExpect(status().isConflict());
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
