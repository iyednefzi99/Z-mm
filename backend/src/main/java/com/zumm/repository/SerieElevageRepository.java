package com.zumm.repository;

import com.zumm.domain.SerieElevage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Series d'elevage (SPRINT-29, lot D). Restreint au tenant. */
public interface SerieElevageRepository extends JpaRepository<SerieElevage, Long> {

    List<SerieElevage> findAllByOrderByDateGreffageDescIdDesc();

    boolean existsByNom(String nom);
}
