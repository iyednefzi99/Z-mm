package com.zumm.repository;

import com.zumm.domain.AuditEntree;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Accès au journal d'audit (US-043). Restreint au tenant courant (@TenantId + RLS),
 * en ajout seul : aucune méthode de mise à jour n'est exposée.
 */
public interface AuditEntreeRepository extends JpaRepository<AuditEntree, Long> {

    /** Les entrées les plus récentes d'abord (vue historique). */
    List<AuditEntree> findTop200ByOrderByInstantDesc();
}
