package com.zumm.repository;

import com.zumm.domain.Planning;
import com.zumm.domain.StatutPlanning;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acces a l'entite {@link Planning} (SPRINT-03). Restreint au tenant (@TenantId + RLS). */
public interface PlanningRepository extends JpaRepository<Planning, Long> {

    /**
     * Plannings d'un agent pour une journee, hors refuses (US-047). La ruche et son
     * site sont charges dans la foulee : la tournee les parcourt tous, un chargement
     * paresseux par planning ferait N+1 requetes.
     *
     * <p>Le tri par heure prevue donne l'ordre initial — celui que l'agent a saisi —
     * qui sert de repli lorsqu'aucun site de depart n'est impose.
     */
    @Query("""
            SELECT p FROM Planning p
            JOIN FETCH p.ruche r
            JOIN FETCH r.site
            WHERE p.agent.id = :agentId
              AND p.datePrevue = :date
              AND p.statut <> :exclu
            ORDER BY p.heurePrevue NULLS LAST, p.id
            """)
    List<Planning> parAgentEtDate(Long agentId, LocalDate date, StatutPlanning exclu);

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
    @EntityGraph(attributePaths = {"ruche", "agent"})
    List<Planning> findAll();
}
