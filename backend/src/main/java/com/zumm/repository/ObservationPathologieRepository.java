package com.zumm.repository;

import com.zumm.domain.ObservationPathologie;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Pathologies constatees en visite (SPRINT-20). Restreint au tenant courant
 * ({@code @TenantId} + RLS).
 */
public interface ObservationPathologieRepository
        extends JpaRepository<ObservationPathologie, Long> {

    /** Pathologies constatees lors d'une visite donnee. */
    List<ObservationPathologie> findByVisite_IdOrderByPathologieAsc(Long visiteId);

    /** Pathologies constatees sur une ruche, toutes visites confondues. */
    List<ObservationPathologie> findByVisite_Ruche_IdOrderByVisite_DateVisiteDescIdDesc(Long rucheId);
}
