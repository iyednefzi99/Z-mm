package com.zumm.repository;

import com.zumm.domain.SuiviReine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Journal de la reine (US-032). Restreint au tenant courant (@TenantId + RLS).
 */
public interface SuiviReineRepository extends JpaRepository<SuiviReine, Long> {

    /** Historique d'une ruche, du plus recent evenement au plus ancien. */
    List<SuiviReine> findByRuche_IdOrderByDateEvenementDescIdDesc(Long rucheId);
}
