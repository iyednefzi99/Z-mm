package com.zumm.repository;

import com.zumm.domain.Recolte;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Recoltes et tracabilite par lot (US-033). Restreint au tenant courant
 * (@TenantId + RLS).
 */
public interface RecolteRepository extends JpaRepository<Recolte, Long> {

    /** Toutes les recoltes, les plus recentes d'abord. */
    List<Recolte> findByOrderByDateRecolteDescIdDesc();

    /** Recolte portant ce numero de lot (tracabilite). */
    Optional<Recolte> findByLot(String lot);

    /** Nombre de recoltes d'une ruche a une date : sert a numeroter le lot. */
    long countByRuche_IdAndDateRecolte(Long rucheId, java.time.LocalDate dateRecolte);
}
