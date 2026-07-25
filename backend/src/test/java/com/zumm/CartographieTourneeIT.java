package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
 * Verifie contre un PostGIS reel le regroupement spatial des sites (US-045), la
 * recherche de voisins (US-046) et l'ordre de tournee (US-047), SPRINT-10.
 *
 * <p>Les coordonnees sont de vraies coordonnees francaises : le regroupement se juge
 * sur des distances reelles (Cahors et Toulouse sont a ~90 km, Paris a ~500 km), pas
 * sur des nombres arbitraires.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CartographieTourneeIT {

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

    // ─── US-045 : regroupement spatial ───

    @Test
    @DisplayName("regroupe les sites proches et numerote les grappes par taille decroissante")
    void regroupeLesSitesProches() throws Exception {
        String t = "grappe-a";
        long fermeId = creerFerme(t);

        // Trois sites autour de Cahors (quelques kilometres entre eux).
        long cahors1 = creerSite(t, fermeId, "Cahors nord", 44.4670, 1.4410);
        creerSite(t, fermeId, "Cahors centre", 44.4470, 1.4410);
        creerSite(t, fermeId, "Cahors sud", 44.4300, 1.4450);
        // Deux sites autour de Toulouse, a ~90 km de Cahors.
        creerSite(t, fermeId, "Toulouse est", 43.6045, 1.4440);
        creerSite(t, fermeId, "Toulouse ouest", 43.5900, 1.4100);
        // Un site isole a Paris.
        creerSite(t, fermeId, "Paris", 48.8566, 2.3522);

        // Deux ruches sur le premier site : le cumul par grappe doit les compter.
        creerRuche(t, fermeId, cahors1);
        creerRuche(t, fermeId, cahors1);

        mockMvc.perform(get("/api/sites/grappes").with(tenant(t))
                        .param("distanceMetres", "15000").param("minimumSites", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                // Grappe 1 : la plus grande (Cahors, 3 sites, 2 ruches).
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].nombreSites").value(3))
                .andExpect(jsonPath("$[0].nombreRuches").value(2))
                .andExpect(jsonPath("$[0].sites.length()").value(3))
                // Grappe 2 : Toulouse, 2 sites, aucune ruche.
                .andExpect(jsonPath("$[1].nombreSites").value(2))
                .andExpect(jsonPath("$[1].nombreRuches").value(0))
                // Grappe 3 : Paris, isole — conserve en singleton, pas perdu.
                .andExpect(jsonPath("$[2].nombreSites").value(1))
                .andExpect(jsonPath("$[2].sites[0].nom").value("Paris"));
    }

    @Test
    @DisplayName("place le centroide d'une grappe entre ses sites")
    void centroideEntreLesSites() throws Exception {
        String t = "grappe-centre";
        long fermeId = creerFerme(t);
        creerSite(t, fermeId, "Sud", 44.4000, 1.4000);
        creerSite(t, fermeId, "Nord", 44.5000, 1.4000);

        mockMvc.perform(get("/api/sites/grappes").with(tenant(t))
                        .param("distanceMetres", "20000").param("minimumSites", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].latitudeCentre").value(44.45))
                .andExpect(jsonPath("$[0].longitudeCentre").value(1.4));
    }

    @Test
    @DisplayName("ne fait jamais entrer le site d'un autre tenant dans une grappe")
    void grappesIsoleesEntreTenants() throws Exception {
        String mien = "grappe-mien";
        String autre = "grappe-autre";
        long maFerme = creerFerme(mien);
        creerSite(mien, maFerme, "Le mien", 44.4470, 1.4410);

        // Site voisin immediat, mais appartenant a un autre tenant.
        long saFerme = creerFerme(autre);
        creerSite(autre, saFerme, "Le sien", 44.4471, 1.4411);

        mockMvc.perform(get("/api/sites/grappes").with(tenant(mien))
                        .param("distanceMetres", "15000").param("minimumSites", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombreSites").value(1))
                .andExpect(jsonPath("$[0].sites[0].nom").value("Le mien"));
    }

    @Test
    @DisplayName("rend une liste vide quand le tenant n'a aucun site")
    void grappesSansAucunSite() throws Exception {
        mockMvc.perform(get("/api/sites/grappes").with(tenant("grappe-vide")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ─── US-046 : voisins les plus proches ───

    @Test
    @DisplayName("classe les voisins par distance geodesique croissante")
    void voisinsParDistanceCroissante() throws Exception {
        String t = "voisin-a";
        long fermeId = creerFerme(t);
        long reference = creerSite(t, fermeId, "Reference", 45.0000, 1.0000);
        creerSite(t, fermeId, "A 8 km", 45.0000, 1.1000);
        creerSite(t, fermeId, "A 1,5 km", 45.0000, 1.0200);
        creerSite(t, fermeId, "A 24 km", 45.0000, 1.3000);

        mockMvc.perform(get("/api/sites/" + reference + "/voisins").with(tenant(t))
                        .param("limite", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].site.nom").value("A 1,5 km"))
                .andExpect(jsonPath("$[1].site.nom").value("A 8 km"))
                // Distance geodesique reelle : ~1,57 km, pas une valeur en degres.
                .andExpect(jsonPath("$[0].distanceMetres").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.greaterThan(1500.0),
                                org.hamcrest.Matchers.lessThan(1650.0))));
    }

    @Test
    @DisplayName("n'expose jamais le site de reference parmi ses propres voisins")
    void voisinsExcluentLaReference() throws Exception {
        String t = "voisin-seul";
        long fermeId = creerFerme(t);
        long reference = creerSite(t, fermeId, "Tout seul", 45.0, 1.0);

        mockMvc.perform(get("/api/sites/" + reference + "/voisins").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("ignore les sites des autres tenants dans le voisinage")
    void voisinsIsolesEntreTenants() throws Exception {
        String mien = "voisin-mien";
        long maFerme = creerFerme(mien);
        long reference = creerSite(mien, maFerme, "Reference", 45.0, 1.0);

        long saFerme = creerFerme("voisin-autre");
        creerSite("voisin-autre", saFerme, "Chez le voisin", 45.0001, 1.0001);

        mockMvc.perform(get("/api/sites/" + reference + "/voisins").with(tenant(mien)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("renvoie 404 pour un site inconnu du tenant")
    void voisinsDunSiteInconnu() throws Exception {
        mockMvc.perform(get("/api/sites/999999/voisins").with(tenant("voisin-404")))
                .andExpect(status().isNotFound());
    }

    // ─── US-047 : ordre de tournee ───

    @Test
    @DisplayName("ordonne les visites du jour par proximite, quel que soit l'ordre de saisie")
    void ordonneLaTournee() throws Exception {
        String t = "tournee-a";
        long fermeId = creerFerme(t);
        long agentId = creerAgent(t, "Amina");

        // Quatre sites alignes d'ouest en est, ~7,9 km entre deux voisins.
        long ouest = creerSite(t, fermeId, "Ouest", 45.0, 1.0);
        long centreOuest = creerSite(t, fermeId, "Centre ouest", 45.0, 1.1);
        long centreEst = creerSite(t, fermeId, "Centre est", 45.0, 1.2);
        long est = creerSite(t, fermeId, "Est", 45.0, 1.3);

        // Saisie volontairement desordonnee : Ouest, Centre est, Centre ouest, Est.
        planifier(t, agentId, creerRuche(t, fermeId, ouest));
        planifier(t, agentId, creerRuche(t, fermeId, centreEst));
        planifier(t, agentId, creerRuche(t, fermeId, centreOuest));
        planifier(t, agentId, creerRuche(t, fermeId, est));
        // Deuxieme ruche sur le site de depart : un seul deplacement, deux visites.
        planifier(t, agentId, creerRuche(t, fermeId, ouest));

        mockMvc.perform(get("/api/plannings/tournee").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", "2026-12-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreSites").value(4))
                .andExpect(jsonPath("$.nombreVisites").value(5))
                .andExpect(jsonPath("$.etapes.length()").value(4))
                .andExpect(jsonPath("$.etapes[0].siteId").value((int) ouest))
                .andExpect(jsonPath("$.etapes[0].nombreVisites").value(2))
                .andExpect(jsonPath("$.etapes[0].distanceDepuisPrecedenteMetres").value(0.0))
                .andExpect(jsonPath("$.etapes[1].siteId").value((int) centreOuest))
                .andExpect(jsonPath("$.etapes[2].siteId").value((int) centreEst))
                .andExpect(jsonPath("$.etapes[3].siteId").value((int) est))
                // Trois sauts de ~7,9 km : le balayage, pas les allers-retours.
                .andExpect(jsonPath("$.distanceTotaleMetres").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.greaterThan(23000.0),
                                org.hamcrest.Matchers.lessThan(24500.0))));
    }

    @Test
    @DisplayName("part du site impose quand l'agent en designe un")
    void respecteLeSiteDeDepart() throws Exception {
        String t = "tournee-depart";
        long fermeId = creerFerme(t);
        long agentId = creerAgent(t, "Bilal");
        long ouest = creerSite(t, fermeId, "Ouest", 45.0, 1.0);
        long centre = creerSite(t, fermeId, "Centre", 45.0, 1.1);
        long est = creerSite(t, fermeId, "Est", 45.0, 1.2);
        planifier(t, agentId, creerRuche(t, fermeId, ouest));
        planifier(t, agentId, creerRuche(t, fermeId, centre));
        planifier(t, agentId, creerRuche(t, fermeId, est));

        mockMvc.perform(get("/api/plannings/tournee").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", "2026-12-04")
                        .param("departSiteId", String.valueOf(est)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapes[0].siteId").value((int) est))
                .andExpect(jsonPath("$.etapes[1].siteId").value((int) centre))
                .andExpect(jsonPath("$.etapes[2].siteId").value((int) ouest));
    }

    @Test
    @DisplayName("refuse un site de depart absent de la tournee du jour")
    void refuseUnDepartHorsTournee() throws Exception {
        String t = "tournee-depart-ko";
        long fermeId = creerFerme(t);
        long agentId = creerAgent(t, "Carla");
        long visite = creerSite(t, fermeId, "Visite", 45.0, 1.0);
        long ailleurs = creerSite(t, fermeId, "Ailleurs", 46.0, 2.0);
        planifier(t, agentId, creerRuche(t, fermeId, visite));

        mockMvc.perform(get("/api/plannings/tournee").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", "2026-12-04")
                        .param("departSiteId", String.valueOf(ailleurs)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une journee sans visite planifiee rend une tournee vide, pas une erreur")
    void tourneeVide() throws Exception {
        String t = "tournee-vide";
        long agentId = creerAgent(t, "Dora");

        mockMvc.perform(get("/api/plannings/tournee").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", "2026-12-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreSites").value(0))
                .andExpect(jsonPath("$.distanceTotaleMetres").value(0))
                .andExpect(jsonPath("$.etapes.length()").value(0));
    }

    @Test
    @DisplayName("une tournee a un seul site tient en une etape sans distance")
    void tourneeAUnSeulSite() throws Exception {
        String t = "tournee-solo";
        long fermeId = creerFerme(t);
        long agentId = creerAgent(t, "Elias");
        long site = creerSite(t, fermeId, "Unique", 45.0, 1.0);
        planifier(t, agentId, creerRuche(t, fermeId, site));

        mockMvc.perform(get("/api/plannings/tournee").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", "2026-12-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.etapes.length()").value(1))
                .andExpect(jsonPath("$.distanceTotaleMetres").value(0));
    }

    @Test
    @DisplayName("exclut de la tournee les plannings refuses par le superviseur")
    void exclutLesPlanningsRefuses() throws Exception {
        String t = "tournee-refus";
        long fermeId = creerFerme(t);
        long agentId = creerAgent(t, "Farid");
        long garde = creerSite(t, fermeId, "Garde", 45.0, 1.0);
        long ecarte = creerSite(t, fermeId, "Ecarte", 45.0, 1.2);
        planifier(t, agentId, creerRuche(t, fermeId, garde));
        long refuse = planifier(t, agentId, creerRuche(t, fermeId, ecarte));

        mockMvc.perform(post("/api/plannings/" + refuse + "/refuser").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motif\":\"Parcelle inondee\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plannings/tournee").with(tenant(t))
                        .param("agentId", String.valueOf(agentId))
                        .param("date", "2026-12-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreSites").value(1))
                .andExpect(jsonPath("$.etapes[0].siteId").value((int) garde));
    }

    // ─── fixtures ───

    private long creerFerme(String t) throws Exception {
        long fermierId = idApres(t, "/api/fermiers", "{\"nom\":\"Fermier " + t + "\",\"contact\":null}");
        return idApres(t, "/api/fermes", "{\"nom\":\"Ferme " + t + "\",\"fermierId\":" + fermierId + "}");
    }

    private long creerSite(String t, long fermeId, String nom, double latitude, double longitude)
            throws Exception {
        return idApres(t, "/api/sites",
                ("{\"nom\":\"%s\",\"fermeId\":%d,\"latitude\":%s,\"longitude\":%s,"
                        + "\"dateMiseEnOeuvre\":\"2026-04-01\"}")
                        .formatted(nom, fermeId, latitude, longitude));
    }

    private long creerRuche(String t, long fermeId, long siteId) throws Exception {
        return idApres(t, "/api/ruches",
                ("{\"modele\":\"Dadant\",\"siteId\":%d,\"fermeId\":%d,"
                        + "\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}")
                        .formatted(siteId, fermeId));
    }

    private long creerAgent(String t, String nom) throws Exception {
        return idApres(t, "/api/agents", "{\"nom\":\"" + nom + "\",\"role\":\"apiculteur\"}");
    }

    private long planifier(String t, long agentId, long rucheId) throws Exception {
        return idApres(t, "/api/plannings",
                ("{\"rucheId\":%d,\"agentId\":%d,\"datePrevue\":\"2026-12-04\",\"raison\":\"controle\"}")
                        .formatted(rucheId, agentId));
    }

    private long idApres(String t, String url, String corps) throws Exception {
        MockHttpServletRequestBuilder requete = post(url).with(tenant(t))
                .contentType(MediaType.APPLICATION_JSON).content(corps);
        String reponse = mockMvc.perform(requete)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(reponse).get("id").asLong();
    }
}
