package com.zumm.repository;

import com.zumm.domain.Consommable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stock de consommables (SPRINT-27). Restreint au tenant.
 */
public interface ConsommableRepository extends JpaRepository<Consommable, Long> {

    List<Consommable> findAllByOrderByCategorieAscLibelleAsc();
}
