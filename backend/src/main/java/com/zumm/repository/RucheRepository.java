package com.zumm.repository;

import com.zumm.domain.Ruche;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux ruches (US-004). Restreint au tenant courant (@TenantId + RLS). Le
 * chargement d'une ruche remonte ses compartiments par cascade.
 */
public interface RucheRepository extends JpaRepository<Ruche, Long> {
}
