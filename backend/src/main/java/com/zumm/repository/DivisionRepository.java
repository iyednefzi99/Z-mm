package com.zumm.repository;

import com.zumm.domain.Division;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Registre des divisions (SPRINT-21). Restreint au tenant ({@code @TenantId} +
 * RLS).
 */
public interface DivisionRepository extends JpaRepository<Division, Long> {

    /** Divisions issues d'une ruche mere, de la plus recente a la plus ancienne. */
    List<Division> findByMere_IdOrderByDateDivisionDescIdDesc(Long mereId);

    /**
     * La division dont une ruche est issue, s'il y en a une.
     *
     * <p>Unique par construction : {@code uq_division_fille} est un index unique
     * partiel. Une ruche n'a donc qu'un seul parent — un arbre genealogique a deux
     * meres serait une saisie fautive, pas une richesse.
     */
    Optional<Division> findByFille_Id(Long filleId);

    /**
     * Filiation d'une ruche dans les deux sens : ce dont elle est issue et ce
     * qu'elle a engendre. Une seule requete plutot que deux, parce que l'ecran
     * de filiation les affiche ensemble.
     */
    @Query("""
            select d from Division d
            where d.mere.id = :rucheId or d.fille.id = :rucheId
            order by d.dateDivision desc, d.id desc
            """)
    List<Division> filiation(@Param("rucheId") Long rucheId);
}
