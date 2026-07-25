package com.zumm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Vérifie le journal d'audit de bout en bout (US-043, SPRINT-09) : une création via
 * l'API dépose une entrée d'audit, consultable par un profil responsable/admin, et
 * refusée aux autres profils.
 */
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/realms/zumm/protocol/openid-connect/certs"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AuditTraceIT {

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

    private JwtRequestPostProcessor responsable(String tenantId) {
        return jwt().jwt(builder -> builder
                        .claim("tenant_id", tenantId)
                        .claim("preferred_username", "resp-test"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_responsable"));
    }

    private JwtRequestPostProcessor apiculteur(String tenantId) {
        return jwt().jwt(builder -> builder.claim("tenant_id", tenantId))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_apiculteur"));
    }

    @Test
    @DisplayName("une création dépose une entrée d'audit, lisible par un responsable")
    void creationTraceeEtLisible() throws Exception {
        String t = "audit-a";

        mockMvc.perform(post("/api/fermiers").with(responsable(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nom\":\"Domaine Audit\",\"contact\":\"a@b.tn\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/audit").with(responsable(t)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("creation"))
                .andExpect(jsonPath("$[0].entite").value("Fermier"))
                .andExpect(jsonPath("$[0].acteur").value("resp-test"))
                .andExpect(jsonPath("$[0].entiteId").isNumber());
    }

    @Test
    @DisplayName("le journal d'audit est refusé à un apiculteur (RBAC)")
    void auditRefuseAuxNonPilotes() throws Exception {
        mockMvc.perform(get("/api/audit").with(apiculteur("audit-b")))
                .andExpect(status().isForbidden());
    }
}
