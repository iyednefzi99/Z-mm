package com.zumm.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

/**
 * Fournit les connexions JDBC a Hibernate en positionnant, sur chacune, la
 * variable de session {@code app.current_tenant} qu'exploitent les politiques RLS
 * PostgreSQL (migration V2).
 *
 * <p>C'est la garantie d'isolation de niveau SGBD voulue par l'ADR-001 : meme si
 * le discriminant applicatif {@code @TenantId} etait contourne, la base ne
 * renverrait que les lignes du tenant courant. Le tenant est passe par
 * {@code set_config(..., ?, false)} en requete preparee — jamais par concatenation
 * — pour ecarter toute injection depuis un claim JWT.
 *
 * <p>A la liberation, la variable est remise a vide : une connexion rendue au pool
 * puis reutilisee hors contexte tenant refuse par defaut, au lieu d'heriter du
 * tenant precedent.
 */
@Component
public class TenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final transient DataSource dataSource;

    public TenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connexion) throws SQLException {
        connexion.close();
    }

    @Override
    public Connection getConnection(String tenantId) throws SQLException {
        Connection connexion = getAnyConnection();
        definirTenant(connexion, tenantId);
        return connexion;
    }

    @Override
    public void releaseConnection(String tenantId, Connection connexion) throws SQLException {
        try {
            definirTenant(connexion, "");
        } finally {
            connexion.close();
        }
    }

    private void definirTenant(Connection connexion, String tenantId) throws SQLException {
        try (PreparedStatement requete =
                connexion.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
            requete.setString(1, tenantId == null ? "" : tenantId);
            requete.execute();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> type) {
        return MultiTenantConnectionProvider.class.equals(type)
                || TenantConnectionProvider.class.equals(type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> type) {
        if (isUnwrappableAs(type)) {
            return (T) this;
        }
        throw new IllegalArgumentException("Type non supporte : " + type);
    }
}
