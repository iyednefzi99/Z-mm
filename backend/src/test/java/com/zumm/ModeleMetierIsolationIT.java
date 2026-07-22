package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;

import com.zumm.domain.Fermier;
import com.zumm.repository.FermierRepository;
import com.zumm.tenant.TenantContext;
import javax.sql.DataSource;
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
 * Prouve l'isolation inter-tenant du modele metier (ADR-001, exigence de test
 * explicite de l'ADR) contre une base PostgreSQL reelle.
 *
 * <p>Deux garanties independantes sont verifiees :
 * <ol>
 *   <li>le <strong>discriminant applicatif</strong> Hibernate ({@code @TenantId}) :
 *       un repository ne voit que les lignes du tenant courant ;</li>
 *   <li>la <strong>RLS PostgreSQL</strong> : meme une requete SQL native, qui
 *       echappe au discriminant, ne renvoie que les lignes du tenant courant —
 *       c'est la garantie de niveau SGBD, celle qui tient si un filtre applicatif
 *       est oublie.</li>
 * </ol>
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@Testcontainers(disabledWithoutDocker = true)
class ModeleMetierIsolationIT {

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

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("pose automatiquement le tenant_id a l'insertion")
    void poseLeTenantAutomatiquement() {
        Fermier enregistre = TenantContext.executer(TENANT_A,
                () -> fermierRepository.save(new Fermier("Rucher du Causse", "causse@example.org")));

        assertThat(enregistre.getTenantId()).isEqualTo(TENANT_A);
        assertThat(enregistre.getId()).isNotNull();
    }

    @Test
    @DisplayName("cache a un tenant les fermiers d'un autre (discriminant applicatif)")
    void isoleLesTenantsAuNiveauApplicatif() {
        Long idA = TenantContext.executer(TENANT_A,
                () -> fermierRepository.save(new Fermier("Miellerie A", null)).getId());

        // Vu par son proprietaire.
        assertThat(TenantContext.executer(TENANT_A, () -> fermierRepository.findById(idA)))
                .as("le proprietaire voit son fermier")
                .isPresent();

        // Invisible pour un autre tenant.
        assertThat(TenantContext.executer(TENANT_B, () -> fermierRepository.findById(idA)))
                .as("un autre tenant ne voit pas ce fermier")
                .isEmpty();
    }

    /**
     * Role non-superutilisateur cree a la volee pour prouver la RLS.
     *
     * <p>ATTENTION — leçon de conception : un <strong>superutilisateur contourne
     * toujours la RLS</strong>, meme avec {@code FORCE ROW LEVEL SECURITY}. Or
     * l'utilisateur applicatif par defaut du conteneur (et de {@code docker-compose})
     * est superutilisateur. La couche RLS ne protege donc <em>reellement</em> que si
     * l'application se connecte avec un role non-superutilisateur. Ce test le
     * verifie explicitement en basculant sur un tel role via {@code SET ROLE}.
     * Le passage de l'application a un role dedie est une tache de durcissement
     * tracee (cf. docs/SPRINT-01-FONDATION.md).
     */
    private static final String ROLE_SONDE = "zumm_rls_sonde";

    @Test
    @DisplayName("la politique RLS masque a un role non-privilegie les lignes d'un autre tenant")
    void isoleLesTenantsAuNiveauSgbd() throws Exception {
        // Insertion sous le tenant A, via l'application (dans sa propre transaction).
        TenantContext.executer(TENANT_A,
                () -> fermierRepository.save(new Fermier("Miellerie RLS", null)));

        // Sous un role NON-superutilisateur, la RLS s'applique : A voit sa ligne,
        // B ne voit rien — alors qu'un superutilisateur verrait tout (ce qui prouve
        // que c'est bien la RLS, et non un autre filtre, qui opere ici).
        assertThat(compterSousRoleNonPrivilegie(TENANT_A))
                .as("le tenant proprietaire voit sa ligne malgre la RLS")
                .isPositive();
        assertThat(compterSousRoleNonPrivilegie(TENANT_B))
                .as("la RLS masque a B toute ligne de A")
                .isZero();
    }

    /** Compte les fermiers visibles pour un tenant, sous un role soumis a la RLS. */
    private long compterSousRoleNonPrivilegie(String tenantId) throws Exception {
        try (var connexion = dataSource.getConnection()) {
            try (var ddl = connexion.createStatement()) {
                ddl.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '"
                        + ROLE_SONDE + "') THEN CREATE ROLE " + ROLE_SONDE + " NOLOGIN; END IF; END $$");
                ddl.execute("GRANT SELECT ON fermier TO " + ROLE_SONDE);
            }
            try {
                try (var pose = connexion.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
                    pose.setString(1, tenantId);
                    pose.execute();
                }
                try (var bascule = connexion.createStatement()) {
                    bascule.execute("SET ROLE " + ROLE_SONDE);
                }
                try (var requete = connexion.createStatement();
                        var resultat = requete.executeQuery("SELECT count(*) FROM fermier")) {
                    resultat.next();
                    return resultat.getLong(1);
                }
            } finally {
                // Connexion rendue au pool : retablir le role et vider la variable,
                // sinon les requetes applicatives suivantes en heriteraient.
                try (var menage = connexion.createStatement()) {
                    menage.execute("RESET ROLE");
                    menage.execute("SELECT set_config('app.current_tenant', '', false)");
                }
            }
        }
    }
}

