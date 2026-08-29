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
 * Verifie le SPRINT-20 contre un PostgreSQL reel : registre sanitaire
 * (traitements, nourrissements, comptages de varroa), observations d'inspection
 * structurees et meteo figee sur la visite.
 *
 * <p>Ce lot repond aux ecarts 1 et 3 de {@code docs/ECART-CONCURRENTS.md}. Ce
 * qu'on eprouve ici n'est pas le CRUD — il est identique a vingt autres — mais
 * les quatre regles qui ne se voient qu'en base : la carence calculee, le
 * denominateur impose par la methode de comptage, l'accord unite/aliment, et le
 * refus par la base d'une cause de cloture sur une ruche encore active.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegistreSanitaireIT {

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

    // ─── Traitements et delai de carence ─────────────────────────────────────

    @Test
    @DisplayName("un traitement enregistre son produit, sa dose et calcule sa date de retrait")
    void traitementEtDateDeRetrait() throws Exception {
        String t = "sp20-traitement";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // Traitement termine, carence de 14 jours : la base calcule le retrait.
        // C'est la colonne generee — si Hibernate ne la relisait pas apres
        // insertion, la reponse porterait `null` sur la seule valeur que
        // l'apiculteur vient chercher.
        mockMvc.perform(post("/api/traitements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"produit":"Apivar",
                                 "substanceActive":"amitraze","cible":"varroa",
                                 "dose":2,"doseUnite":"laniere",
                                 "dateDebut":"2026-07-01","dateFin":"2026-07-11",
                                 "delaiCarenceJours":14}""").formatted(rucheId, agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.produit").value("Apivar"))
                .andExpect(jsonPath("$.substanceActive").value("amitraze"))
                .andExpect(jsonPath("$.dateRetrait").value("2026-07-25"));

        mockMvc.perform(get("/api/traitements").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cible").value("varroa"));
    }

    @Test
    @DisplayName("un traitement en cours bloque la recolte, meme sans date de retrait")
    void carenceEnCours() throws Exception {
        String t = "sp20-carence";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // Sans `dateFin`, la carence n'a pas commence a s'ecouler : la ruche
        // doit apparaitre bloquee. L'omettre autoriserait a recolter PENDANT le
        // traitement.
        creer(t, "/api/traitements", ("""
                {"rucheId":%d,"agentId":%d,"produit":"Apiguard","cible":"varroa",
                 "dateDebut":"%s","delaiCarenceJours":10}""")
                .formatted(rucheId, agentId, LocalDate.now().minusDays(2)));

        mockMvc.perform(get("/api/traitements/carence").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sousCarence").value(true))
                .andExpect(jsonPath("$[0].dateRetrait").doesNotExist());
    }

    @Test
    @DisplayName("une dose sans unite est refusee : « 2 » ne dit ni 2 ml ni 2 lanieres")
    void doseSansUnite() throws Exception {
        String t = "sp20-dose";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        mockMvc.perform(post("/api/traitements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                                 "dose":2,"dateDebut":"2026-07-01"}""").formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une fin de traitement anterieure a son debut est refusee")
    void periodeInversee() throws Exception {
        String t = "sp20-periode";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        mockMvc.perform(post("/api/traitements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                                 "dateDebut":"2026-07-11","dateFin":"2026-07-01"}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("une cible hors referentiel est refusee")
    void cibleInconnue() throws Exception {
        String t = "sp20-cible";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        mockMvc.perform(post("/api/traitements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"produit":"X","cible":"grippe",
                                 "dateDebut":"2026-07-01"}""").formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Nourrissements ──────────────────────────────────────────────────────

    @Test
    @DisplayName("un nourrissement enregistre type, quantite et motif")
    void nourrissement() throws Exception {
        String t = "sp20-nourri";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        mockMvc.perform(post("/api/nourrissements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateApport":"2026-09-15",
                                 "typeAliment":"sirop_2_1","quantite":4.5,"quantiteUnite":"l",
                                 "motif":"hivernage"}""").formatted(rucheId, agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.typeAliment").value("sirop_2_1"))
                .andExpect(jsonPath("$.motif").value("hivernage"));

        mockMvc.perform(get("/api/nourrissements").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantiteUnite").value("l"));
    }

    @Test
    @DisplayName("l'unite doit correspondre a l'aliment : pas de sirop en kilos")
    void uniteIncoherente() throws Exception {
        String t = "sp20-unite";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // Plausible au clavier, absurde au rucher — et un bilan de saison bati
        // sur des unites melangees est faux sans jamais paraitre l'etre.
        mockMvc.perform(post("/api/nourrissements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateApport":"2026-09-15",
                                 "typeAliment":"sirop_1_1","quantite":3,"quantiteUnite":"kg"}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/nourrissements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateApport":"2026-09-15",
                                 "typeAliment":"candi","quantite":3,"quantiteUnite":"l"}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Comptages de varroa ─────────────────────────────────────────────────

    @Test
    @DisplayName("un comptage rend son taux AVEC son unite, et le verdict qui va avec")
    void comptageVarroa() throws Exception {
        String t = "sp20-varroa";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // 9 varroas sur 300 abeilles = 3 % : le seuil de declenchement.
        mockMvc.perform(post("/api/varroa").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateComptage":"2026-08-01",
                                 "methode":"sucre_glace","varroasComptes":9,
                                 "abeillesEchantillon":300}""").formatted(rucheId, agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taux").value(3.00))
                .andExpect(jsonPath("$.tauxUnite").value("pour_cent_abeilles"))
                .andExpect(jsonPath("$.verdict").value("traiter"));

        // 3 varroas en 3 jours de lange = 1 par jour : anodin. Meme nombre brut
        // que ci-dessus a l'unite pres, verdict oppose — c'est l'argument contre
        // une colonne « taux » unique.
        mockMvc.perform(post("/api/varroa").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateComptage":"2026-08-05",
                                 "methode":"lange","varroasComptes":3,
                                 "joursExposition":3}""").formatted(rucheId, agentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tauxUnite").value("varroas_par_jour"))
                .andExpect(jsonPath("$.verdict").value("faible"));

        mockMvc.perform(get("/api/varroa").with(tenant(t))
                        .param("rucheId", String.valueOf(rucheId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("chaque methode exige SON denominateur, et refuse celui de l'autre")
    void denominateurImpose() throws Exception {
        String t = "sp20-denom";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // Lange sans duree : incalculable.
        mockMvc.perform(post("/api/varroa").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateComptage":"2026-08-01",
                                 "methode":"lange","varroasComptes":12,
                                 "abeillesEchantillon":300}""").formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());

        // Echantillon sans nombre d'abeilles : incalculable aussi.
        mockMvc.perform(post("/api/varroa").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateComptage":"2026-08-01",
                                 "methode":"alcool","varroasComptes":12,
                                 "joursExposition":3}""").formatted(rucheId, agentId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un comptage se supprime, et un identifiant inconnu rend 404")
    void suppressionComptage() throws Exception {
        String t = "sp20-suppr";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        long id = creer(t, "/api/varroa", ("""
                {"rucheId":%d,"agentId":%d,"dateComptage":"2026-08-01",
                 "methode":"co2","varroasComptes":4,"abeillesEchantillon":300}""")
                .formatted(rucheId, agentId));

        mockMvc.perform(delete("/api/varroa/" + id).with(tenant(t)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/varroa/" + id).with(tenant(t)))
                .andExpect(status().isNotFound());
    }

    // ─── Observations structurees, meteo figee et pathologies ────────────────

    @Test
    @DisplayName("une visite porte sa grille d'inspection, sa meteo figee et ses pathologies")
    void visiteStructuree() throws Exception {
        String t = "sp20-visite";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        long visiteId = creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-20","raison":"controle",
                 "constatations":"RAS",
                 "observation":{"couvainOeufs":true,"couvainLarves":true,"motifPonte":"compact",
                                "reineVue":true,"cellulesRoyales":3,
                                "cellulesRoyalesCause":"essaimage",
                                "cadresCouvain":6,"cadresMiel":4,"temperament":"doux"},
                 "meteo":{"temperatureCelsius":22.5,"humiditePourcent":60,"ventKmh":8.0,
                          "source":"open-meteo"},
                 "pathologies":[{"pathologie":"varroose","gravite":"moderee"},
                                {"pathologie":"fausse_teigne"}]}""")
                .formatted(rucheId, agentId));

        mockMvc.perform(get("/api/visites/" + visiteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observation.motifPonte").value("compact"))
                .andExpect(jsonPath("$.observation.cellulesRoyales").value(3))
                .andExpect(jsonPath("$.observation.cellulesRoyalesCause").value("essaimage"))
                .andExpect(jsonPath("$.observation.temperament").value("doux"))
                .andExpect(jsonPath("$.meteo.temperatureCelsius").value(22.5))
                .andExpect(jsonPath("$.meteo.source").value("open-meteo"))
                .andExpect(jsonPath("$.pathologies.length()").value(2))
                // La gravite omise retombe sur « suspectee » : au rucher on
                // constate un symptome, on ne pose pas un diagnostic.
                .andExpect(jsonPath("$.pathologies[?(@.pathologie=='fausse_teigne')].gravite")
                        .value("suspectee"));
    }

    @Test
    @DisplayName("une visite sans grille rend `observation` et `meteo` absents, pas remplis de nulls")
    void visiteSansGrille() throws Exception {
        String t = "sp20-eclair";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // Un objet plein de `null` ferait croire a une grille remplie de « non ».
        long visiteId = creer(t, "/api/visites", ("""
                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-21","raison":"autre"}""")
                .formatted(rucheId, agentId));

        mockMvc.perform(get("/api/visites/" + visiteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.observation").doesNotExist())
                .andExpect(jsonPath("$.meteo").doesNotExist())
                .andExpect(jsonPath("$.pathologies.length()").value(0));
    }

    @Test
    @DisplayName("une cause de cellules royales sans cellules est refusee par la base")
    void causeSansCellules() throws Exception {
        String t = "sp20-cellules";
        long rucheId = chaineRuche(t);
        long agentId = agent(t);

        // « supersedure, zero cellule » est une contradiction, pas une
        // observation. La contrainte est en base parce qu'elle porte sur
        // l'accord de deux colonnes — et `GestionnaireExceptions` traduit la
        // violation en 409, pas en 500 : c'est bien la requete qui est en tort,
        // pas le serveur.
        mockMvc.perform(post("/api/visites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"dateVisite":"2026-05-22",
                                 "observation":{"cellulesRoyalesCause":"supersedure"}}""")
                                .formatted(rucheId, agentId)))
                .andExpect(status().isConflict());
    }

    // ─── Referentiel de ruche ────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche porte son type au referentiel, sa couleur et son origine")
    void referentielRuche() throws Exception {
        String t = "sp20-ruche";
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        long fermeId = creer(t, "/api/fermes",
                "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
        long siteId = creer(t, "/api/sites",
                "{\"nom\":\"S\",\"fermeId\":" + fermeId
                        + ",\"latitude\":45.0,\"longitude\":1.0,\"dateMiseEnOeuvre\":\"2026-04-01\"}");

        mockMvc.perform(post("/api/ruches").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"modele":"Dadant 10 cadres, fond Nicot","siteId":%d,"fermeId":%d,
                                 "typeRuche":"dadant","couleur":"bleu","origine":"division",
                                 "compartiments":[{"type":"corps","nbCadres":10}]}""")
                                .formatted(siteId, fermeId)))
                .andExpect(status().isCreated())
                // Le modele reste le texte libre, le type est le referentiel
                // au-dessus : c'est lui qui rend une statistique par type possible.
                .andExpect(jsonPath("$.modele").value("Dadant 10 cadres, fond Nicot"))
                .andExpect(jsonPath("$.typeRuche").value("dadant"))
                .andExpect(jsonPath("$.couleur").value("bleu"))
                .andExpect(jsonPath("$.origine").value("division"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private long agent(String t) throws Exception {
        return creer(t, "/api/agents",
                "{\"nom\":\"Amine Trabelsi\",\"role\":\"apiculteur\",\"email\":null}");
    }

    private long chaineRuche(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        long fermeId = creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
        long siteId = creer(t, "/api/sites",
                "{\"nom\":\"S\",\"fermeId\":" + fermeId
                        + ",\"latitude\":45.0,\"longitude\":1.0,\"dateMiseEnOeuvre\":\"2026-04-01\"}");
        return creer(t, "/api/ruches",
                "{\"modele\":\"M\",\"siteId\":" + siteId + ",\"fermeId\":" + fermeId
                        + ",\"compartiments\":[{\"type\":\"corps\",\"nbCadres\":10}]}");
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
