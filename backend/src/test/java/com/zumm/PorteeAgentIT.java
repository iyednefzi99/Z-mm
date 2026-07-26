package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;

import com.zumm.domain.Agent;
import com.zumm.domain.Ferme;
import com.zumm.domain.Fermier;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.FermierRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.tenant.PorteeContext;
import com.zumm.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Portee d'autorisation par affectation d'agent (US-057).
 *
 * <p><strong>Pourquoi ces tests passent par une connexion directe sous le role
 * applicatif</strong>, et non par MockMvc : l'application se connecte, en test,
 * avec le role PROPRIETAIRE de la base, lequel contourne la RLS meme sous
 * {@code FORCE ROW LEVEL SECURITY}. Un test passant par l'API ne prouverait donc
 * rien de la restriction — il ne verrait que le discriminant applicatif
 * d'Hibernate. C'est la meme raison qui a conduit {@code RoleApplicatifIT} a
 * ouvrir une connexion sous {@code zumm_app} ; on reprend ce dispositif.
 *
 * <p>Ce qui est etabli ici :
 * <ol>
 *   <li>un agent ne voit QUE les ruches dont il est responsable ;
 *   <li>il ne voit que les SITES qui portent l'une de ses ruches — c'est-a-dire
 *       qu'il ne peut pas enumerer la carte des ruchers ;
 *   <li>il ne voit que les MESURES de ses ruches : le poids d'une ruche dit si
 *       elle vaut d'etre volee ;
 *   <li>une portee globale (responsable, admin) voit tout ;
 *   <li><strong>l'absence de portee ne vaut pas « tout voir »</strong> — c'est le
 *       mode de defaillance qui compte.
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@Testcontainers(disabledWithoutDocker = true)
class PorteeAgentIT {

    private static final String ROLE_APP = "zumm_app";
    private static final String MOTDEPASSE_APP = "zumm_app_dev";
    private static final String TENANT = "exploitation-portee";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("zumm/test-postgres:16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    @Autowired private FermierRepository fermiers;
    @Autowired private FermeRepository fermes;
    @Autowired private SiteRepository sites;
    @Autowired private RucheRepository ruches;
    @Autowired private AgentRepository agents;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private Long agentAlice;
    private Long agentBruno;
    private Long siteAlice;
    private Long siteBruno;
    private Long rucheAlice;
    private Long rucheBruno;

    /**
     * Deux agents, deux sites, deux ruches : le decor minimal pour qu'une fuite
     * soit visible. Le seed passe par le proprietaire, qui contourne la RLS —
     * c'est voulu, il joue le role de l'administrateur.
     */
    @BeforeEach
    void semer() {
        if (agentAlice != null) {
            return;
        }
        TenantContext.executer(TENANT, () -> {
            Fermier fermier = fermiers.save(new Fermier("Exploitation portee", null));
            Ferme ferme = fermes.save(new Ferme("Ferme unique", fermier));

            Agent alice = new Agent("Alice", RoleAgent.APICULTEUR, ferme);
            alice.setEmail("alice@example.invalid");
            Agent bruno = new Agent("Bruno", RoleAgent.APICULTEUR, ferme);
            bruno.setEmail("bruno@example.invalid");
            agentAlice = agents.save(alice).getId();
            agentBruno = agents.save(bruno).getId();

            Site sa = sites.save(new Site("Rucher d'Alice", ferme,
                    new BigDecimal("44.100000"), new BigDecimal("1.100000"), LocalDate.now()));
            Site sb = sites.save(new Site("Rucher de Bruno", ferme,
                    new BigDecimal("44.200000"), new BigDecimal("1.200000"), LocalDate.now()));
            siteAlice = sa.getId();
            siteBruno = sb.getId();

            Ruche ra = new Ruche("Dadant Alice", sa, ferme, com.zumm.domain.EtatRuche.CREEE);
            ra.setAgentResponsable(agents.findById(agentAlice).orElseThrow());
            Ruche rb = new Ruche("Dadant Bruno", sb, ferme, com.zumm.domain.EtatRuche.CREEE);
            rb.setAgentResponsable(agents.findById(agentBruno).orElseThrow());
            rucheAlice = ruches.save(ra).getId();
            rucheBruno = ruches.save(rb).getId();
            return null;
        });
    }

    /** Connexion sous le role applicatif REEL, seul endroit ou la RLS mord. */
    private Connection connexionApplicative() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), ROLE_APP, MOTDEPASSE_APP);
    }

    /** Pose les trois variables de session que lisent les politiques. */
    private void poser(Connection connexion, boolean globale, Long agentId) throws SQLException {
        try (var pose = connexion.prepareStatement(
                "SELECT set_config('app.current_tenant', ?, false),"
                        + " set_config('app.portee_globale', ?, false),"
                        + " set_config('app.agent_courant', ?, false)")) {
            pose.setString(1, TENANT);
            pose.setString(2, Boolean.toString(globale));
            pose.setString(3, agentId == null ? "" : String.valueOf(agentId));
            pose.execute();
        }
    }

    private long compter(Connection connexion, String table) throws SQLException {
        try (var requete = connexion.prepareStatement("SELECT count(*) FROM " + table);
                var resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getLong(1);
        }
    }

    private boolean voitLigne(Connection connexion, String table, long id) throws SQLException {
        try (var requete = connexion.prepareStatement("SELECT count(*) FROM " + table + " WHERE id = ?")) {
            requete.setLong(1, id);
            try (var resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getLong(1) > 0;
            }
        }
    }

    @Test
    @DisplayName("un agent ne voit que les ruches dont il est responsable")
    void agentNeVoitQueSesRuches() throws SQLException {
        try (Connection connexion = connexionApplicative()) {
            poser(connexion, false, agentAlice);

            assertThat(voitLigne(connexion, "ruche", rucheAlice))
                    .as("Alice doit voir sa propre ruche").isTrue();
            assertThat(voitLigne(connexion, "ruche", rucheBruno))
                    .as("Alice ne doit pas voir la ruche de Bruno").isFalse();
            assertThat(compter(connexion, "ruche")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("un agent ne voit que les sites qui portent l'une de ses ruches")
    void agentNeVoitQueSesSites() throws SQLException {
        // C'est LA table sensible : la liste des sites est la carte des ruchers.
        try (Connection connexion = connexionApplicative()) {
            poser(connexion, false, agentAlice);

            assertThat(voitLigne(connexion, "site", siteAlice)).isTrue();
            assertThat(voitLigne(connexion, "site", siteBruno))
                    .as("le site de Bruno ne doit pas etre enumerable par Alice").isFalse();
        }
    }

    @Test
    @DisplayName("un agent ne voit pas les mesures des ruches d'un autre")
    void agentNeVoitPasLesMesuresDesAutres() throws SQLException {
        try (Connection connexion = connexionApplicative()) {
            // Deux mesures posees par le proprietaire, une par ruche.
            try (Connection proprietaire = DriverManager.getConnection(
                    postgres.getJdbcUrl(), "zumm", "zumm_secure");
                    var insertion = proprietaire.prepareStatement(
                            "INSERT INTO mesure (tenant_id, ruche_id, type_indicateur, instant, valeur)"
                                    + " VALUES (?, ?, 'poids', now(), 42.0) ON CONFLICT DO NOTHING")) {
                for (Long ruche : new Long[] {rucheAlice, rucheBruno}) {
                    insertion.setString(1, TENANT);
                    insertion.setLong(2, ruche);
                    insertion.executeUpdate();
                }
            }

            poser(connexion, false, agentAlice);
            assertThat(compter(connexion, "mesure"))
                    .as("le poids d'une ruche dit si elle vaut d'etre volee")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("une portee globale voit toute l'exploitation")
    void porteeGlobaleVoitTout() throws SQLException {
        try (Connection connexion = connexionApplicative()) {
            poser(connexion, true, null);

            assertThat(voitLigne(connexion, "ruche", rucheAlice)).isTrue();
            assertThat(voitLigne(connexion, "ruche", rucheBruno)).isTrue();
            assertThat(voitLigne(connexion, "site", siteAlice)).isTrue();
            assertThat(voitLigne(connexion, "site", siteBruno)).isTrue();
        }
    }

    @Test
    @DisplayName("l'absence de portee ne vaut PAS « tout voir »")
    void absenceDePorteeNeVautPasTout() throws SQLException {
        // Le mode de defaillance qui compte. Un contexte non pose — tache
        // planifiee, connexion du pool reprise, test mal isole — doit ne rien
        // voir, jamais tout voir.
        try (Connection connexion = connexionApplicative()) {
            try (var pose = connexion.prepareStatement(
                    "SELECT set_config('app.current_tenant', ?, false)")) {
                pose.setString(1, TENANT);
                pose.execute();
            }

            assertThat(compter(connexion, "ruche")).isZero();
            assertThat(compter(connexion, "site")).isZero();
        }
    }

    @Test
    @DisplayName("l'application POSE bien les variables sur la connexion Hibernate")
    void applicationPoseLesVariables() {
        // Les tests precedents prouvent que les POLITIQUES restreignent. Celui-ci
        // ferme l'autre moitie : que l'application transmette effectivement la
        // portee a la base. Sans lui, des politiques justes pourraient rester
        // lettre morte, faute d'etre alimentees — et rien ne le signalerait,
        // puisque l'application se connecte en test avec le proprietaire, qui
        // contourne la RLS.
        var lu = TenantContext.executer(TENANT, () ->
                PorteeContext.executer(PorteeContext.Portee.agent(agentAlice), () ->
                        (Object[]) entityManager.createNativeQuery(
                                "SELECT current_setting('app.current_tenant', true),"
                                        + " current_setting('app.portee_globale', true),"
                                        + " current_setting('app.agent_courant', true)")
                                .getSingleResult()));

        assertThat(lu[0]).isEqualTo(TENANT);
        assertThat(lu[1]).as("un agent de terrain n'a pas la portee globale").isEqualTo("false");
        assertThat(lu[2]).isEqualTo(String.valueOf(agentAlice));
    }

    @Test
    @DisplayName("la portee ne peut pas franchir la frontiere d'exploitation")
    void laPorteeNeFranchitPasLeTenant() throws SQLException {
        // Les deux gardes se cumulent : meme avec l'identifiant d'agent exact,
        // un autre tenant ne rend rien. La portee restreint, elle n'elargit jamais.
        try (Connection connexion = connexionApplicative()) {
            try (var pose = connexion.prepareStatement(
                    "SELECT set_config('app.current_tenant', 'exploitation-etrangere', false),"
                            + " set_config('app.portee_globale', 'true', false),"
                            + " set_config('app.agent_courant', ?, false)")) {
                pose.setString(1, String.valueOf(agentAlice));
                pose.execute();
            }

            assertThat(compter(connexion, "ruche")).isZero();
            assertThat(compter(connexion, "site")).isZero();
        }
    }
}
