package com.zumm.repository;

import com.zumm.domain.Materiel;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Inventaire du materiel (SPRINT-27). Restreint au tenant.
 */
public interface MaterielRepository extends JpaRepository<Materiel, Long> {

    List<Materiel> findAllByOrderByCategorieAscLibelleAsc();

    /** Ce qui a une periodicite : le seul materiel que le moteur de regles regarde. */
    List<Materiel> findByPeriodiciteJoursIsNotNull();
}
