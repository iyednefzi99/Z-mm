package com.zumm.repository;

import com.zumm.domain.Ruche;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acces aux ruches (US-004). Restreint au tenant courant (@TenantId + RLS). Le
 * chargement d'une ruche remonte ses compartiments par cascade.
 */
public interface RucheRepository extends JpaRepository<Ruche, Long> {

    /**
     * Nombre de ruches par site, pour le tenant courant (US-045). Requete JPQL : le
     * discriminant {@code @TenantId} s'y applique sans filtre explicite. Chaque ligne
     * est un couple {@code [siteId, compte]}.
     */
    @Query("SELECT r.site.id, COUNT(r) FROM Ruche r GROUP BY r.site.id")
    List<Object[]> comptesParSite();
}
