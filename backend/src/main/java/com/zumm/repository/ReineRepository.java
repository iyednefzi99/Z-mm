package com.zumm.repository;

import com.zumm.domain.Reine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux reines et a leur filiation (SPRINT-29, lot D). Restreint au tenant.
 *
 * <p>A ne pas confondre avec {@link SuiviReineRepository}, qui sert le JOURNAL
 * d'une ruche. Les deux coexistent, et c'est voulu : l'un porte les evenements,
 * l'autre les individus.
 */
public interface ReineRepository extends JpaRepository<Reine, Long> {

    List<Reine> findAllByOrderByIdDesc();

    /** Filles directes d'une reine, pour descendre l'arbre un niveau a la fois. */
    List<Reine> findByMere_IdOrderByIdAsc(Long mereId);

    List<Reine> findByRuche_IdOrderByIdDesc(Long rucheId);

    boolean existsByCode(String code);
}
