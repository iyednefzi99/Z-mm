package com.zumm.repository;

import com.zumm.domain.AbonnementCalendrier;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Jetons d'abonnement iCalendar (SPRINT-21).
 *
 * <p><strong>Aucune de ces methodes n'est filtree par tenant</strong> : l'entite
 * ne porte pas {@code @TenantId} et la table n'a pas de politique RLS, parce
 * qu'elle est lue avant que le tenant soit connu. Le cloisonnement est donc
 * APPLICATIF, et c'est pour cela que les deux methodes de gestion prennent le
 * tenant en parametre explicite : oublier de le passer serait une fuite entre
 * exploitations, et la signature ne le permet pas.
 */
public interface AbonnementCalendrierRepository extends JpaRepository<AbonnementCalendrier, Long> {

    /** Resolution du jeton : le seul acces legitimement sans tenant. */
    Optional<AbonnementCalendrier> findByJetonEmpreinte(String jetonEmpreinte);

    List<AbonnementCalendrier> findByTenantIdAndAgentIdOrderByCreeLeDesc(
            String tenantId, Long agentId);

    Optional<AbonnementCalendrier> findByIdAndTenantId(Long id, String tenantId);
}
