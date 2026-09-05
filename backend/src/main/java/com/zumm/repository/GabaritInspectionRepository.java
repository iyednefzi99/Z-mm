package com.zumm.repository;

import com.zumm.domain.GabaritInspection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux gabarits d'inspection (SPRINT-28). Restreint au tenant
 * ({@code @TenantId} + RLS).
 */
public interface GabaritInspectionRepository extends JpaRepository<GabaritInspection, Long> {

    List<GabaritInspection> findAllByOrderByNomAsc();

    /**
     * Le gabarit propose par defaut, s'il y en a un.
     *
     * <p>Un seul peut exister : l'index unique partiel {@code uq_gabarit_defaut}
     * le garantit, et c'est ce qui rend legitime de rendre un {@link Optional}
     * plutot qu'une liste.
     */
    Optional<GabaritInspection> findByParDefautTrue();

    boolean existsByNom(String nom);
}
