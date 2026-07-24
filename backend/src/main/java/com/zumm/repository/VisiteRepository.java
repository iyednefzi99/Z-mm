package com.zumm.repository;

import com.zumm.domain.Visite;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces a l'entite {@link Visite} (SPRINT-03). Restreint au tenant (@TenantId + RLS). */
public interface VisiteRepository extends JpaRepository<Visite, Long> {
}
