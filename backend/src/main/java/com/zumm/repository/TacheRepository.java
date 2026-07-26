package com.zumm.repository;

import com.zumm.domain.Tache;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux taches et rappels (US-031). Restreint au tenant courant
 * (@TenantId + RLS) : aucun filtre {@code tenant_id} n'est a ecrire ici.
 */
public interface TacheRepository extends JpaRepository<Tache, Long> {

    /** Taches non faites dont l'echeance tombe au plus tard le {@code jour} donne (rappels). */
    List<Tache> findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(LocalDate jour);

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
    List<Tache> findAll();
}
