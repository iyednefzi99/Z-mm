package com.zumm.tenant;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Fournit a Hibernate l'identifiant du tenant courant.
 *
 * <p>Hibernate s'en sert pour renseigner {@code tenant_id} a l'insertion et pour
 * l'ajouter comme discriminant a chaque lecture/mise a jour/suppression des
 * entites annotees {@code @TenantId}. La valeur vient de {@link TenantContext}.
 *
 * <p>Hors contexte tenant, on renvoie {@link #SANS_TENANT} — une valeur qui ne
 * correspond a aucune ligne : une requete emise sans tenant ne voit donc rien,
 * plutot que de lever une erreur ou, pire, de tout exposer.
 *
 * <p>Enregistre aupres d'Hibernate par {@code MultiTenancyHibernateConfig}.
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    /** Sentinelle hors contexte : ne correspond a aucun tenant reel. */
    public static final String SANS_TENANT = "__sans_tenant__";

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.courant().orElse(SANS_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
