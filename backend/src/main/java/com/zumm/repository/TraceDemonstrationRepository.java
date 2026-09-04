package com.zumm.repository;

import com.zumm.domain.TraceDemonstration;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Trace du jeu de demonstration (SPRINT-25). Restreint au tenant.
 */
public interface TraceDemonstrationRepository extends JpaRepository<TraceDemonstration, Long> {

    /** Du plus dependant au moins dependant : c'est l'ordre de purge. */
    List<TraceDemonstration> findAllByOrderByOrdreAscEntiteIdDesc();
}
