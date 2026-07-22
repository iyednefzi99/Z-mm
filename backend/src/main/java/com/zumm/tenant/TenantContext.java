package com.zumm.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Porte le tenant courant pour le fil d'execution en cours.
 *
 * <p>Rempli par {@link TenantFilter} a partir du claim {@code tenant_id} du jeton
 * JWT, il est lu par {@link TenantIdentifierResolver} (filtre Hibernate) et par
 * {@link TenantConnectionProvider} (variable de session RLS). En dehors d'un
 * contexte tenant, la valeur est absente : les deux couches renvoient alors zero
 * ligne (refus par defaut).
 *
 * <p>Un {@code ThreadLocal} convient au modele un-thread-par-requete de Spring MVC.
 * Il doit imperativement etre nettoye en fin de traitement — d'ou {@link #executer}
 * pour les usages hors requete (tests, taches planifiees).
 */
public final class TenantContext {

    private static final ThreadLocal<String> COURANT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** Definit le tenant courant. {@code null} ou vide efface le contexte. */
    public static void definir(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            COURANT.remove();
        } else {
            COURANT.set(tenantId);
        }
    }

    /** Tenant courant, s'il existe. */
    public static Optional<String> courant() {
        return Optional.ofNullable(COURANT.get());
    }

    /** Efface le contexte. A appeler en fin de traitement pour ne pas fuiter. */
    public static void effacer() {
        COURANT.remove();
    }

    /**
     * Execute une action dans un contexte tenant donne, puis restaure l'etat
     * precedent. Sur pour les taches hors requete HTTP.
     */
    public static <T> T executer(String tenantId, Supplier<T> action) {
        String precedent = COURANT.get();
        definir(tenantId);
        try {
            return action.get();
        } finally {
            definir(precedent);
        }
    }
}
