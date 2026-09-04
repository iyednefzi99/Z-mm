package com.zumm.repository;

import com.zumm.domain.RessourceFlorale;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Ressources florales declarees autour des ruchers (SPRINT-21). Restreint au
 * tenant ET a la portee de l'agent (RLS, V20).
 */
public interface RessourceFloraleRepository extends JpaRepository<RessourceFlorale, Long> {

    List<RessourceFlorale> findBySite_IdOrderByRessourceAsc(Long siteId);

    void deleteBySite_Id(Long siteId);
}
