package com.zumm.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Active le multi-tenant Hibernate (ADR-001) en enregistrant explicitement les
 * deux briques, sans dependre de l'auto-detection de Spring Boot (dont le
 * comportement varie selon les versions) :
 *
 * <ul>
 *   <li>{@link TenantConnectionProvider} — fournit les connexions et positionne la
 *       variable de session {@code app.current_tenant} lue par les politiques RLS ;</li>
 *   <li>{@link TenantIdentifierResolver} — designe le tenant courant, exploite par
 *       le discriminant applicatif {@code @TenantId}.</li>
 * </ul>
 *
 * <p>Les entites annotees {@code @TenantId} sont alors filtrees a deux niveaux :
 * discriminant Hibernate <em>et</em> RLS PostgreSQL.
 */
@Configuration
public class MultiTenancyHibernateConfig implements HibernatePropertiesCustomizer {

    private final TenantConnectionProvider fournisseurDeConnexion;
    private final TenantIdentifierResolver resolveurDeTenant;

    public MultiTenancyHibernateConfig(TenantConnectionProvider fournisseurDeConnexion,
            TenantIdentifierResolver resolveurDeTenant) {
        this.fournisseurDeConnexion = fournisseurDeConnexion;
        this.resolveurDeTenant = resolveurDeTenant;
    }

    @Override
    public void customize(java.util.Map<String, Object> proprietesHibernate) {
        proprietesHibernate.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, fournisseurDeConnexion);
        proprietesHibernate.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolveurDeTenant);
    }
}
