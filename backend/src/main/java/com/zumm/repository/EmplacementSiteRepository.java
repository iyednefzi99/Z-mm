package com.zumm.repository;

import com.zumm.domain.EmplacementSite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Historique des emplacements d'un rucher (SPRINT-21). Restreint au tenant ET a
 * la portee de l'agent (RLS, V20) : cette table est une carte des ruchers dans le
 * temps.
 */
public interface EmplacementSiteRepository extends JpaRepository<EmplacementSite, Long> {

    /** Historique complet, du plus recent au plus ancien. */
    List<EmplacementSite> findBySite_IdOrderByDateDebutDescIdDesc(Long siteId);

    /**
     * Emplacement courant d'un site — celui dont la fin n'est pas posee.
     *
     * <p>L'unicite n'est pas garantie ici mais en base ({@code uq_emplacement_courant},
     * index unique partiel) : deux emplacements ouverts sur le meme site sont
     * refuses par PostgreSQL, ce qui rend cette recherche deterministe.
     */
    Optional<EmplacementSite> findBySite_IdAndDateFinIsNull(Long siteId);
}
