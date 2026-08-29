package com.zumm.repository;

import com.zumm.domain.Nourrissement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Registre des nourrissements (SPRINT-20). Restreint au tenant courant
 * ({@code @TenantId} + RLS).
 */
public interface NourrissementRepository extends JpaRepository<Nourrissement, Long> {

    /** Apports d'une ruche, du plus recent au plus ancien. */
    List<Nourrissement> findByRuche_IdOrderByDateApportDescIdDesc(Long rucheId);
}
