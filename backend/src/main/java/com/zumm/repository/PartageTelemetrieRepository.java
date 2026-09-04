package com.zumm.repository;

import com.zumm.domain.PartageTelemetrie;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Partages de telemetrie (SPRINT-26). Comme {@code AbonnementCalendrierRepository},
 * ce depot travaille SANS discriminant de tenant : la table le porte sans le
 * discriminer, puisque le jeton est ce qui le resout. Chaque methode de gestion
 * exige donc le tenant explicitement, et le service est le seul appelant.
 */
public interface PartageTelemetrieRepository extends JpaRepository<PartageTelemetrie, Long> {

    /** Resolution du jeton : le seul acces legitimement sans tenant. */
    Optional<PartageTelemetrie> findByJetonEmpreinte(String jetonEmpreinte);

    List<PartageTelemetrie> findByTenantIdAndRucheIdOrderByCreeLeDesc(
            String tenantId, Long rucheId);

    Optional<PartageTelemetrie> findByIdAndTenantId(Long id, String tenantId);
}
