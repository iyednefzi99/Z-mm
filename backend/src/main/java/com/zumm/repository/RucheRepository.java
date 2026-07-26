package com.zumm.repository;

import com.zumm.domain.Ruche;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * Listage complet, associations chargees en une seule requete (SPRINT-14).
     *
     * <p>Sans ce graphe, chaque ligne rendue declenchait une requete de plus pour
     * lire le libelle de son parent : le fameux « N+1 ». Invisible sur les
     * dizaines de lignes d'une demonstration, il devient le poste de cout
     * dominant sur un parc reel — 500 ruches, c'est 501 aller-retours la ou un
     * seul suffit.
     */
    @Override
    @EntityGraph(attributePaths = {"site", "ferme", "compartiments"})
    List<Ruche> findAll();
}
