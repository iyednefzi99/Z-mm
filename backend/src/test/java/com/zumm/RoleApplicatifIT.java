package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zumm.domain.Fermier;
import com.zumm.repository.FermierRepository;
import com.zumm.tenant.TenantContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
 * Prouve le durcissement RLS (ADR-001) sur le role applicatif REEL {@code zumm_app}
 * cree par la migration V3.
 *
 * <p>Contrairement a {@code ModeleMetierIsolationIT} qui teste la politique via un
 * role de sonde, ce test ouvre une <strong>connexion directe</strong> avec le role
 * exact que l'application utilisera en production, et verifie que :
 * <ol>
 *   <li>il est <strong>non-superutilisateur</strong> (condition sine qua non pour
 *       que la RLS s'applique) ;</li>
 *   <li>la <strong>RLS l'isole reellement</strong> : il voit les lignes de son
 *       tenant, pas celles d'un autre ;</li>
 *   <li>il est <strong>au moindre privilege</strong> : le DML lui est ouvert, le
 *       DDL lui est refuse.</li>
 * </ol>
 *
 * <p>Les migrations restent executees par le proprietaire {@code zumm} (droits
 * DDL) ; seule l'application bascule sur {@code zumm_app}.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@Testcontainers(disabledWithoutDocker = true)
class RoleApplicatifIT {

    private static final String ROLE_APP = "zumm_app";
    private static final String MOTDEPASSE_APP = "zumm_app_dev";
    private static final String TENANT_A = "exploitation-a";
    private static final String TENANT_B = "exploitation-b";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("zumm/test-postgres:16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("zumm")
            .withUsername("zumm")
            .withPassword("zumm_secure")
            .withCommand("postgres", "-c", "shared_preload_libraries=timescaledb");

    @Autowired
    private FermierRepository fermierRepository;

    /** Connexion directe avec le role applicatif reel (et non le proprietaire). */
    private Connection connexionApplicative() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), ROLE_APP, MOTDEPASSE_APP);
    }

    @Test
    @DisplayName("le role applicatif existe et n'est PAS superutilisateur")
    void roleApplicatifNonSuperutilisateur() throws SQLException {
        try (Connection connexion = connexionApplicative();
                var requete = connexion.prepareStatement("SHOW is_superuser");
                var resultat = requete.executeQuery()) {
            resultat.next();
            assertThat(resultat.getString(1))
                    .as("zumm_app doit etre non-superutilisateur pour que la RLS s'applique")
                    .isEqualTo("off");
        }
    }

    @Test
    @DisplayName("la RLS isole reellement le role applicatif entre tenants")
    void rlsEffectivePourLeRoleApplicatif() throws SQLException {
        // Seed sous le tenant A via l'application (proprietaire).
        TenantContext.executer(TENANT_A,
                () -> fermierRepository.save(new Fermier("Miellerie durcie", null)));

        try (Connection connexion = connexionApplicative()) {
            assertThat(compter(connexion, TENANT_A))
                    .as("le role applicatif voit les fermiers de son tenant")
                    .isPositive();
            assertThat(compter(connexion, TENANT_B))
                    .as("la RLS masque au role applicatif les fermiers d'un autre tenant")
                    .isZero();
        }
    }

    @Test
    @DisplayName("le role applicatif a le DML mais pas le DDL (moindre privilege)")
    void moindrePrivilege() throws SQLException {
        try (Connection connexion = connexionApplicative()) {
            // DML autorise : une lecture sous un tenant ne leve pas d'erreur de droits.
            compter(connexion, TENANT_A);

            // DDL refuse : le role ne doit pas pouvoir modifier le schema.
            assertThatThrownBy(() -> {
                try (var ddl = connexion.createStatement()) {
                    ddl.execute("DROP TABLE fermier");
                }
            }).isInstanceOf(SQLException.class);
        }
    }

    /** Compte les fermiers visibles pour un tenant, sur une connexion donnee. */
    private long compter(Connection connexion, String tenantId) throws SQLException {
        try (var pose = connexion.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
            pose.setString(1, tenantId);
            pose.execute();
        }
        try (var requete = connexion.prepareStatement("SELECT count(*) FROM fermier");
                var resultat = requete.executeQuery()) {
            resultat.next();
            return resultat.getLong(1);
        }
    }
}
