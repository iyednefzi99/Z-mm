package com.zumm.repository;

import com.zumm.domain.ComptageVarroa;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Comptages de varroa (SPRINT-20). Restreint au tenant courant
 * ({@code @TenantId} + RLS).
 */
public interface ComptageVarroaRepository extends JpaRepository<ComptageVarroa, Long> {

    /** Serie d'une ruche, du comptage le plus recent au plus ancien. */
    List<ComptageVarroa> findByRuche_IdOrderByDateComptageDescIdDesc(Long rucheId);

    /**
     * Dernier comptage d'une ruche.
     *
     * <p>Utile seul : c'est lui qui dit ou en est la pression parasitaire
     * aujourd'hui, la ou la serie complete sert a la tendance.
     */
    Optional<ComptageVarroa> findFirstByRuche_IdOrderByDateComptageDescIdDesc(Long rucheId);
}
