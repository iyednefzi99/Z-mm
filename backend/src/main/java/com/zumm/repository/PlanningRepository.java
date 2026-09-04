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

    /**
     * Plannings d'une periode, hors statut exclu, pour l'export iCalendar
     * (SPRINT-21). La ruche, son site et l'agent sont charges dans la meme
     * requete : le fichier les cite tous, et un chargement paresseux par
     * evenement ferait N+1 requetes pour produire un simple texte.
     */
    @Query("""
            SELECT p FROM Planning p
            JOIN FETCH p.ruche r
            JOIN FETCH r.site
            JOIN FETCH p.agent
            WHERE p.datePrevue BETWEEN :debut AND :fin
              AND p.statut <> :exclu
            ORDER BY p.datePrevue, p.heurePrevue NULLS LAST, p.id
            """)
    List<Planning> parPeriode(LocalDate debut, LocalDate fin, StatutPlanning exclu);

    /**
     * Meme fenetre, bornee a UN agent : c'est ce que publie un abonnement
     * iCalendar (SPRINT-21).
     *
     * <p>Le filtre applicatif sur l'agent double la portee posee par la RLS. Ce
     * n'est pas une redondance inutile : en test, l'application se connecte avec
     * le role proprietaire de la base, qui contourne la RLS. Sans ce `where`, le
     * flux publierait tout le parc sous un jeton nominatif, et aucun test ne le
     * verrait.
     */
    @Query("""
            SELECT p FROM Planning p
            JOIN FETCH p.ruche r
            JOIN FETCH r.site
            JOIN FETCH p.agent
            WHERE p.agent.id = :agentId
              AND p.datePrevue BETWEEN :debut AND :fin
              AND p.statut <> :exclu
            ORDER BY p.datePrevue, p.heurePrevue NULLS LAST, p.id
            """)
    List<Planning> parPeriodeEtAgent(Long agentId, LocalDate debut, LocalDate fin,
            StatutPlanning exclu);
}
