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
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class WalkingSkeletonIT {

    private static final DockerImageName IMAGE = DockerImageName
            .parse("timescale/timescaledb-ha:pg16-ts2.14")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PingRepository pingRepository;

    @Autowired
    private DataSource dataSource;

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
}
