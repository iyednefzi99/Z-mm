package com.zumm.repository;

import com.zumm.domain.Depense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Depenses de l'exploitation (SPRINT-27). Restreint au tenant.
 */
public interface DepenseRepository extends JpaRepository<Depense, Long> {

    List<Depense> findAllByOrderByDateDepenseDescIdDesc();

    /** Depenses d'une periode : la maille de tout bilan. */
    List<Depense> findByDateDepenseBetweenOrderByDateDepenseDescIdDesc(
            LocalDate debut, LocalDate fin);
}
