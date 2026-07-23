package com.zumm.repository;

import com.zumm.domain.Site;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acces a l'entite {@link Site}. Les requetes JPA sont automatiquement restreintes
 * au tenant courant : filtre applicatif Hibernate ({@code @TenantId}) double par la
 * politique RLS PostgreSQL.
 */
public interface SiteRepository extends JpaRepository<Site, Long> {

    /**
     * Identifiants des sites du tenant courant situes a moins de {@code rayonMetres}
     * du point donne (US-003, requete spatiale PostGIS). Renvoie des identifiants,
     * pas des entites : le rechargement via {@code findAllById} repasse par le
     * filtre {@code @TenantId}.
     *
     * <p>Le filtre {@code tenant_id} y est EXPLICITE : une requete native echappe au
     * discriminant Hibernate, et la RLS ne protege pas si l'application se connecte
     * en superutilisateur (cf. durcissement V3). On lit donc la variable de session
     * directement — sur, quel que soit le role.
     */
    @Query(value = """
            SELECT id FROM site
            WHERE tenant_id = current_setting('app.current_tenant', true)
              AND ST_DWithin(
                    geog,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :rayonMetres)
            ORDER BY geog <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            """, nativeQuery = true)
    List<Long> idsProches(@Param("latitude") double latitude,
                          @Param("longitude") double longitude,
                          @Param("rayonMetres") double rayonMetres);
}
