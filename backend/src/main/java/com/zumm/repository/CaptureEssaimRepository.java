package com.zumm.repository;

import com.zumm.domain.CaptureEssaim;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Registre des captures d'essaim (SPRINT-21). Restreint au tenant
 * ({@code @TenantId} + RLS).
 */
public interface CaptureEssaimRepository extends JpaRepository<CaptureEssaim, Long> {

    List<CaptureEssaim> findAllByOrderByDateCaptureDescIdDesc();

    /** Captures d'une saison : la seule maille qui compte pour juger une annee. */
    List<CaptureEssaim> findByDateCaptureBetweenOrderByDateCaptureDescIdDesc(
            LocalDate debut, LocalDate fin);

    /** Captures pas encore logees dans une ruche : la liste de ce qui reste a faire. */
    List<CaptureEssaim> findByRucheIsNullOrderByDateCaptureDescIdDesc();
}
