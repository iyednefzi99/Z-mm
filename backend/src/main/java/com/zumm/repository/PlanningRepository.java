package com.zumm.repository;

import com.zumm.domain.Planning;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces a l'entite {@link Planning} (SPRINT-03). Restreint au tenant (@TenantId + RLS). */
public interface PlanningRepository extends JpaRepository<Planning, Long> {
}
