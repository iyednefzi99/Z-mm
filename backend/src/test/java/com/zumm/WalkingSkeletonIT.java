package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zumm.domain.Ping;
import com.zumm.repository.PingRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verification de bout en bout du walking skeleton du SPRINT-00.
 *
 * <p>Demarre l'application contre une base PostgreSQL reelle — la meme image que
 * {@code infra/docker-compose.yml}, PostGIS et TimescaleDB inclus — applique les
 * migrations Flyway, persiste une entite et interroge l'etat de sante.
 *
 * <p>Le test est ignore automatiquement lorsque Docker est indisponible, afin
 * qu'un poste sans demon ne fasse pas echouer le build. En integration continue,
 * Docker est present : le test s'execute donc reellement et fait autorite.
 */
@SpringBootTest(properties = {
        // `issuer-uri` declencherait une decouverte OIDC au demarrage : le contexte
        // ne monterait pas sans Keycloak. `jwk-set-uri` est resolu paresseusement,
        // a la premiere validation de jeton — ce test n'en presente aucun.
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WalkingSkeletonIT {

    /**
     * Image de test PostGIS + TimescaleDB, a construire au prealable :
     *
     * <pre>docker build -f infra/test-postgres.Dockerfile -t zumm/test-postgres:16 infra/</pre>
     *
     * <p>Elle n'est volontairement pas construite par Testcontainers : le Dockerfile
     * utilise des montages de cache APT, indispensables sur liaison lente, que seul
     * BuildKit comprend — or Testcontainers passe par le builder historique de l'API
     * Docker et echoue sur {@code --mount}.
     *
     * <p>La cible d'execution reste {@code timescale/timescaledb-ha}
     * (cf. {@code infra/docker-compose.yml}) : seule la base de test differe.
     */
    private static final DockerImageName IMAGE = DockerImageName
            .parse("zumm/test-postgres:16")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            // TimescaleDB doit etre precharge pour que CREATE EXTENSION reussisse.
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PingRepository pingRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry registre;

    @Test
    @DisplayName("applique les migrations et active PostGIS et TimescaleDB")
    void appliqueLesMigrationsEtLesExtensions() throws Exception {
        try (var connexion = dataSource.getConnection();
                var requete = connexion.prepareStatement(
                        "SELECT extname FROM pg_extension WHERE extname IN ('postgis', 'timescaledb')")) {
            var extensions = new java.util.ArrayList<String>();
            try (var resultat = requete.executeQuery()) {
                while (resultat.next()) {
                    extensions.add(resultat.getString("extname"));
                }
            }
            assertThat(extensions).containsExactlyInAnyOrder("postgis", "timescaledb");
        }
    }

    @Test
    @DisplayName("persiste une entite et la relit depuis la base")
    void persisteEtRelitUneEntite() {
        Ping enregistre = pingRepository.save(new Ping("walking skeleton"));

        assertThat(enregistre.getId()).isNotNull();
        assertThat(pingRepository.findById(enregistre.getId()))
                .isPresent()
                .get()
                .satisfies(ping -> {
                    assertThat(ping.getLibelle()).isEqualTo("walking skeleton");
                    assertThat(ping.getCreeLe()).isNotNull();
                });
    }

    @Test
    @DisplayName("declare l'application en bonne sante, base comprise")
    void declareApplicationEnBonneSante() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("enregistre les metriques interrogees par le tableau de bord Grafana")
    void enregistreLesMetriquesDuTableauDeBord() throws Exception {
        // Une requete d'abord, pour que la serie des requetes HTTP naisse.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // Memes series que infra/monitoring/grafana/dashboards/zumm-socle.json :
        // si elles disparaissent, le tableau de bord se vide en silence.
        // (Le nom Micrometer utilise des points ; Prometheus les traduit en
        // underscores a l'export — http.server.requests -> http_server_requests.)
        assertThat(registre.find("http.server.requests").timer())
                .as("metrique des requetes HTTP").isNotNull();
        assertThat(registre.find("hikaricp.connections.active").gauge())
                .as("metrique du pool JDBC").isNotNull();

        // Tag commun applique a toutes les series, cible du filtre du dashboard.
        assertThat(registre.getMeters())
                .anySatisfy(metrique -> assertThat(metrique.getId().getTag("application"))
                        .isEqualTo("zumm"));
    }
}
