package com.zumm.repository;

import com.zumm.domain.GabaritPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Points retenus par un gabarit (SPRINT-28). Restreint au tenant. */
public interface GabaritPointRepository
        extends JpaRepository<GabaritPoint, GabaritPoint.Cle> {

    List<GabaritPoint> findByIdGabaritIdOrderByOrdreAsc(Long gabaritId);
}
