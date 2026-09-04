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
 * Eprouve le SPRINT-23 lot B contre un PostgreSQL reel : actes de lot et
 * agregats au niveau du rucher, de l'emplacement et de l'equipe.
 *
 * <p>Le socle du lot a ses tests unitaires ({@code OperationsLotServiceTest}) ;
 * ce qui ne se voit qu'ici, c'est ce qui traverse la base et la serialisation :
 *
 * <ol>
 *   <li>un lot partiel s'ecrit VRAIMENT — les reussites restent en base apres le
 *       refus d'une ligne, ce que seule une transaction par ruche permet ;
 *   <li>la synthese d'un rucher jamais visite rend {@code null}, et non zero :
 *       la difference entre « en mauvaise sante » et « inconnu » se perd a la
 *       moindre moyenne mal posee ;
 *   <li>la comparaison d'emplacements ne rend AUCUNE coordonnee — c'est
 *       exactement l'ecran ou l'on serait tente d'en mettre ;
 *   <li>comparer un seul emplacement est refuse : ce n'est pas une comparaison ;
 *   <li>la charge d'equipe compte les ruchers CONCERNES, pas les ruches : trois
 *       ruches sur trois ruchers font trois deplacements.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class LotEtAgregatsIT {

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

    // ─── Actes de lot ────────────────────────────────────────────────────────

    @Test
    @DisplayName("traiter tout un rucher ecrit une ligne par ruche vivante")
    void traitementDeRucherEntier() throws Exception {
        String t = "sp23-lot-traitement";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId, "Rucher du causse", 44.44, 1.44, "haute");
        long agentId = agent(t, "Amine Trabelsi");
        long premiere = ruche(t, siteId, fermeId, agentId);
        long seconde = ruche(t, siteId, fermeId, agentId);

        mockMvc.perform(post("/api/traitements/lot").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"cible":{"rucheIds":null,"siteId":%d},
                                 "traitement":{"rucheId":%d,"agentId":%d,"produit":"Apivar",
                                  "substanceActive":"amitraze","cible":"varroa",
                                  "dose":2,"doseUnite":"laniere","dateDebut":"2026-08-01",
                                  "dateFin":"2026-09-15","delaiCarenceJours":30}}""")
                                .formatted(siteId, premiere, agentId)))
                // 200 et un rapport, jamais 201 : aucune des ressources creees
                // n'est « la » ressource creee.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandees").value(2))
                .andExpect(jsonPath("$.reussites.length()").value(2))
                .andExpect(jsonPath("$.echecs.length()").value(0));

        // Les deux registres portent bien le traitement : un rapport qui annonce
        // deux reussites sans rien ecrire serait le pire des deux mondes.
        for (long id : new long[] {premiere, seconde}) {
            mockMvc.perform(get("/api/traitements?rucheId=" + id).with(tenant(t)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].produit").value("Apivar"));
        }
    }

    @Test
    @DisplayName("un refus de carence n'annule pas les recoltes deja ecrites du meme lot")
    void recolteDeLotPartielle() throws Exception {
        String t = "sp23-lot-recolte";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId, "Rucher des tilleuls", 44.45, 1.45, null);
        long agentId = agent(t, "Sonia Haddad");
        long saine = ruche(t, siteId, fermeId, agentId);
        long traitee = ruche(t, siteId, fermeId, agentId);

        // Une seule des deux ruches est sous carence, et elle le restera : la
        // date de fin est posee dans l'avenir du jour de test.
        mockMvc.perform(post("/api/traitements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"produit":"Apivar","cible":"varroa",
                                 "dateDebut":"%s","delaiCarenceJours":365}""")
                                .formatted(traitee, agentId, java.time.LocalDate.now())))
                .andExpect(status().isCreated());

        String rapport = mockMvc.perform(post("/api/recoltes/lot").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"cible":{"rucheIds":null,"siteId":%d},
                                 "dateRecolte":"%s","quantiteKgParRuche":12.5,
                                 "typeMiel":"toutes fleurs"}""")
                                .formatted(siteId, java.time.LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demandees").value(2))
                .andExpect(jsonPath("$.reussites.length()").value(1))
                .andExpect(jsonPath("$.echecs.length()").value(1))
                .andExpect(jsonPath("$.echecs[0].rucheId").value(traitee))
                .andReturn().getResponse().getContentAsString();

        // Le motif est celui du service metier, pas un message generique : sans
        // lui, le rejeu porterait sur les deux ruches au lieu d'une.
        String motif = json.readTree(rapport).get("echecs").get(0).get("motif").asText();
        org.assertj.core.api.Assertions.assertThat(motif).containsIgnoringCase("carence");

        // La recolte de la ruche saine a bel et bien ete ecrite : c'est le point
        // du lot partiel, et il ne se verifie qu'en relisant la base.
        mockMvc.perform(get("/api/recoltes").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rucheId").value(saine));
    }

    @Test
    @DisplayName("nourrir un rucher entier, puis une ruche cloturee nommement designee")
    void nourrissementDeLotEtRucheCloturee() throws Exception {
        String t = "sp23-lot-nourrissement";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId, "Rucher d'hivernage", 44.46, 1.46, null);
        long agentId = agent(t, "Karim Belhadj");
        long vivante = ruche(t, siteId, fermeId, agentId);

        mockMvc.perform(post("/api/nourrissements/lot").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"cible":{"rucheIds":[%d],"siteId":%d},
                                 "nourrissement":{"rucheId":%d,"agentId":%d,
                                  "dateApport":"2026-09-20","typeAliment":"sirop_2_1",
                                  "quantite":3,"quantiteUnite":"l","motif":"hivernage"}}""")
                                .formatted(vivante, siteId, vivante, agentId)))
                .andExpect(status().isOk())
                // Designer la ruche ET son rucher ne la nourrit qu'une fois.
                .andExpect(jsonPath("$.demandees").value(1))
                .andExpect(jsonPath("$.reussites.length()").value(1));

        // Une cible vide est une erreur d'appel, pas un lot de zero ligne.
        mockMvc.perform(post("/api/nourrissements/lot").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"cible":{"rucheIds":null,"siteId":null},
                                 "nourrissement":{"rucheId":%d,"agentId":%d,
                                  "dateApport":"2026-09-20","typeAliment":"candi",
                                  "quantite":1,"quantiteUnite":"kg"}}""")
                                .formatted(vivante, agentId)))
                .andExpect(status().isBadRequest());
    }

    // ─── Synthese par rucher ─────────────────────────────────────────────────

    @Test
    @DisplayName("un rucher jamais visite rend une sante nulle, pas zero")
    void syntheseSansObservation() throws Exception {
        String t = "sp23-synthese";
        long fermeId = ferme(t);
        long siteId = siteDe(t, fermeId, "Rucher neuf", 44.47, 1.47, "haute");
        long agentId = agent(t, "Ines Gharbi");
        ruche(t, siteId, fermeId, agentId);
        ruche(t, siteId, fermeId, agentId);

        mockMvc.perform(get("/api/ruchers/synthese?siteId=" + siteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].siteNom").value("Rucher neuf"))
                .andExpect(jsonPath("$[0].priorite").value("haute"))
                .andExpect(jsonPath("$[0].nbRuches").value(2))
                .andExpect(jsonPath("$[0].nbActives").value(2))
                // Le point du test : compter une colonie non visitee comme 0
                // ferait chuter un rucher qu'on n'a pas encore vu, et comme 100
                // le ferait mentir dans l'autre sens.
                .andExpect(jsonPath("$[0].santeMoyenne").doesNotExist())
                .andExpect(jsonPath("$[0].coloniesEvaluees").value(0))
                .andExpect(jsonPath("$[0].risqueEssaimageMax").doesNotExist())
                .andExpect(jsonPath("$[0].ruchesSousCarence").value(0));

        // Sans siteId, tous les ruchers du tenant.
        mockMvc.perform(get("/api/ruchers/synthese").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("un rucher sous carence remonte en tete de la synthese")
    void syntheseTrieeParPreoccupation() throws Exception {
        String t = "sp23-synthese-tri";
        long fermeId = ferme(t);
        long calme = siteDe(t, fermeId, "Rucher calme", 44.10, 1.10, null);
        long soigne = siteDe(t, fermeId, "Rucher sous traitement", 44.20, 1.20, null);
        long agentId = agent(t, "Nadia Chaabane");
        ruche(t, calme, fermeId, agentId);
        ruche(t, calme, fermeId, agentId);
        long traitee = ruche(t, soigne, fermeId, agentId);

        mockMvc.perform(post("/api/traitements").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"agentId":%d,"produit":"Apiguard","cible":"varroa",
                                 "dateDebut":"%s","delaiCarenceJours":365}""")
                                .formatted(traitee, agentId, java.time.LocalDate.now())))
                .andExpect(status().isCreated());

        // L'ordre est celui du travail — ce qui a des colonies a soigner d'abord,
        // et non le plus gros rucher ni le premier par ordre alphabetique.
        mockMvc.perform(get("/api/ruchers/synthese").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].siteId").value(soigne))
                .andExpect(jsonPath("$[0].ruchesSousCarence").value(1))
                .andExpect(jsonPath("$[1].siteId").value(calme));
    }

    @Test
    @DisplayName("la synthese d'un rucher inconnu est un 404, pas un rucher vide")
    void syntheseRucherInconnu() throws Exception {
        mockMvc.perform(get("/api/ruchers/synthese?siteId=999999").with(tenant("sp23-404")))
                .andExpect(status().isNotFound());
    }

    // ─── Comparaison d'emplacements ──────────────────────────────────────────

    @Test
    @DisplayName("comparer deux emplacements aligne des criteres, sans jamais rendre de position")
    void comparaisonSansPosition() throws Exception {
        String t = "sp23-comparaison";
        long fermeId = ferme(t);
        long agentId = agent(t, "Yosra Mbarek");

        long plateau = creer(t, "/api/sites", ("""
                {"nom":"Plateau","fermeId":%d,"latitude":44.80,"longitude":1.80,
                 "altitude":620,"dateMiseEnOeuvre":"2026-03-01","ville":"Gramat",
                 "typeSite":"transhumance","exposition":"sud",
                 "ressources":[{"ressource":"tilleul","distanceM":400,"note":null},
                               {"ressource":"acacia","distanceM":1800,"note":null}]}""")
                .formatted(fermeId));
        long vallee = creer(t, "/api/sites", ("""
                {"nom":"Vallee","fermeId":%d,"latitude":44.81,"longitude":1.81,
                 "altitude":180,"dateMiseEnOeuvre":"2026-03-01","ville":"Figeac",
                 "typeSite":"sedentaire","exposition":"est"}""").formatted(fermeId));

        long uneDuPlateau = ruche(t, plateau, fermeId, agentId);
        ruche(t, plateau, fermeId, agentId);

        mockMvc.perform(post("/api/recoltes").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"rucheId":%d,"dateRecolte":"%s","quantiteKg":20,
                                 "typeMiel":"tilleul"}""")
                                .formatted(uneDuPlateau, java.time.LocalDate.now())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/sites/comparaison?ids=" + plateau + "," + vallee)
                        .with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // L'ordre demande est conserve : le reordonner reviendrait a
                // repondre a la place de l'apiculteur.
                .andExpect(jsonPath("$[0].siteId").value(plateau))
                .andExpect(jsonPath("$[0].nbRuches").value(2))
                // Rendement PAR RUCHE : 20 kg sur deux colonies, pas 20 kg.
                .andExpect(jsonPath("$[0].rendementKgParRuche").value(10.00))
                .andExpect(jsonPath("$[0].ressourcesDeclarees").value(2))
                // Les deux ruchers sont a moins d'un kilometre : ce n'est pas
                // deux emplacements, c'est un seul.
                .andExpect(jsonPath("$[0].ruchersA3km").value(1))
                // Un rucher sans colonie n'a pas un rendement de zero : il n'en a
                // pas. La difference est tout l'objet de la comparaison.
                .andExpect(jsonPath("$[1].nbRuches").value(0))
                .andExpect(jsonPath("$[1].rendementKgParRuche").doesNotExist())
                // Comparer trois emplacements est exactement le moment ou l'on
                // serait tente d'en donner les coordonnees.
                .andExpect(jsonPath("$[0].latitude").doesNotExist())
                .andExpect(jsonPath("$[0].longitude").doesNotExist());
    }

    @Test
    @DisplayName("comparer un seul emplacement, ou un emplacement inconnu, est refuse")
    void comparaisonRefusee() throws Exception {
        String t = "sp23-comparaison-refus";
        long siteId = siteDe(t, ferme(t), "Seul", 44.90, 1.90, null);

        mockMvc.perform(get("/api/sites/comparaison?ids=" + siteId).with(tenant(t)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/sites/comparaison?ids=" + siteId + ",999999").with(tenant(t)))
                .andExpect(status().isBadRequest());
    }

    // ─── Charge de l'equipe ──────────────────────────────────────────────────

    @Test
    @DisplayName("la charge compte les ruchers concernes, pas les ruches")
    void chargeParRuchersConcernes() throws Exception {
        String t = "sp23-charge";
        long fermeId = ferme(t);
        long premier = siteDe(t, fermeId, "Rucher un", 44.30, 1.30, null);
        long second = siteDe(t, fermeId, "Rucher deux", 44.31, 1.31, null);
        long charge = agent(t, "Hedi Mansour");
        long libre = agent(t, "Rania Ayari");

        long uneIci = ruche(t, premier, fermeId, charge);
        ruche(t, premier, fermeId, charge);
        ruche(t, second, fermeId, charge);

        // Une tache en retard et une critique, pour l'agent charge.
        creer(t, "/api/taches", ("""
                {"libelle":"Poser les hausses","rucheId":%d,"agentId":%d,
                 "echeance":"2026-01-05","faite":false,"priorite":"critique",
                 "categorie":"materiel"}""").formatted(uneIci, charge));
        creer(t, "/api/taches", ("""
                {"libelle":"Controler la ponte","rucheId":%d,"agentId":%d,
                 "faite":false,"priorite":"normale","categorie":"controle"}""")
                .formatted(uneIci, charge));

        mockMvc.perform(get("/api/equipe/charge").with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // Le plus charge d'abord : c'est la ligne sur laquelle on agit.
                .andExpect(jsonPath("$[0].agentId").value(charge))
                .andExpect(jsonPath("$[0].ruchesResponsable").value(3))
                // Trois ruches sur deux ruchers font deux deplacements, pas trois.
                .andExpect(jsonPath("$[0].ruchersConcernes").value(2))
                .andExpect(jsonPath("$[0].tachesOuvertes").value(2))
                .andExpect(jsonPath("$[0].tachesEnRetard").value(1))
                .andExpect(jsonPath("$[0].tachesCritiques").value(1))
                .andExpect(jsonPath("$[1].agentId").value(libre))
                .andExpect(jsonPath("$[1].ruchesResponsable").value(0))
                .andExpect(jsonPath("$[1].tachesOuvertes").value(0));
    }

    // ─── Priorite de terrain ─────────────────────────────────────────────────

    @Test
    @DisplayName("une priorite hors des trois niveaux est refusee avant la base")
    void prioriteHorsReferentiel() throws Exception {
        String t = "sp23-priorite";
        long fermeId = ferme(t);

        mockMvc.perform(post("/api/sites").with(tenant(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("""
                                {"nom":"S","fermeId":%d,"latitude":44.0,"longitude":1.0,
                                 "dateMiseEnOeuvre":"2026-04-01","priorite":"urgente"}""")
                                .formatted(fermeId)))
                .andExpect(status().isBadRequest());

        // Absente, la priorite vaut `normale` : un defaut nul obligerait chaque
        // lecture a le traiter comme un cas particulier.
        long siteId = siteDe(t, fermeId, "Sans priorite", 44.0, 1.0, null);
        mockMvc.perform(get("/api/sites/" + siteId).with(tenant(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priorite").value("normale"));
    }

    // ─── Jeu d'essai ─────────────────────────────────────────────────────────

    private long ferme(String t) throws Exception {
        long fermierId = creer(t, "/api/fermiers", "{\"nom\":\"F\",\"contact\":null}");
        return creer(t, "/api/fermes", "{\"nom\":\"Fe\",\"fermierId\":" + fermierId + "}");
    }

    private long siteDe(String t, long fermeId, String nom, double lat, double lon,
            String priorite) throws Exception {
        return creer(t, "/api/sites", ("""
                {"nom":"%s","fermeId":%d,"latitude":%s,"longitude":%s,
                 "dateMiseEnOeuvre":"2026-04-01","priorite":%s}""")
                .formatted(nom, fermeId, lat, lon,
                        priorite == null ? "null" : "\"" + priorite + "\""));
    }

    private long ruche(String t, long siteId, long fermeId, long agentId) throws Exception {
        return creer(t, "/api/ruches", ("""
                {"modele":"M","siteId":%d,"fermeId":%d,"agentResponsableId":%d,
                 "compartiments":[{"type":"corps","nbCadres":10}]}""")
                .formatted(siteId, fermeId, agentId));
    }

    private long agent(String t, String nom) throws Exception {
        return creer(t, "/api/agents",
                "{\"nom\":\"" + nom + "\",\"role\":\"apiculteur\",\"email\":null}");
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
