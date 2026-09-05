package com.zumm.repository;

import com.zumm.domain.Traitement;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Registre des traitements (SPRINT-20). Restreint au tenant courant
 * ({@code @TenantId} + RLS).
 */
public interface TraitementRepository extends JpaRepository<Traitement, Long> {

    /** Traitements d'une periode, pour le registre d'elevage (SPRINT-29). */
    List<Traitement> findByDateDebutBetweenOrderByDateDebutAscRuche_IdAsc(
            java.time.LocalDate debut, java.time.LocalDate fin);

    /** Registre d'une ruche, du traitement le plus recent au plus ancien. */
    List<Traitement> findByRuche_IdOrderByDateDebutDescIdDesc(Long rucheId);

    /**
     * Traitements dont la carence n'est pas echue au jour donne.
     *
     * <p>C'est la requete qui repond a « puis-je recolter cette ruche ? ». Deux
     * cas y tombent, et le second est celui qu'on oublie :
     *
     * <ul>
     *   <li>le traitement est termine mais sa {@code date_retrait} est encore a
     *       venir ;
     *   <li>le traitement <strong>court toujours</strong> ({@code date_fin} nulle)
     *       — la carence n'a meme pas commence a s'ecouler. L'omettre
     *       autoriserait a recolter pendant le traitement lui-meme.
     * </ul>
     */
    @Query("""
            select t from Traitement t
            where t.delaiCarenceJours is not null
              and t.dateDebut <= :jour
              and (t.dateFin is null or t.dateRetrait is null or t.dateRetrait >= :jour)
            order by t.ruche.id, t.dateDebut desc
            """)
    List<Traitement> sousCarenceAu(@Param("jour") LocalDate jour);
}
