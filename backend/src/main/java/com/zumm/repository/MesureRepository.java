package com.zumm.repository;

import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux mesures (US-016), cle composite. Restreint au tenant (@TenantId + RLS). */
public interface MesureRepository extends JpaRepository<Mesure, MesureId> {
}
