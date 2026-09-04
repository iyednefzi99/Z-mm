package com.zumm.repository;

import com.zumm.domain.Fermier;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces a l'entite {@link Fermier}. Les requetes sont automatiquement restreintes au
 * tenant courant : filtre applicatif Hibernate ({@code @TenantId}) double par la
 * politique RLS PostgreSQL. Aucun filtre {@code tenant_id} n'est donc a ecrire ici.
 */
public interface FermierRepository extends JpaRepository<Fermier, Long> {

    /** Fermiers dont le nom contient {@code motif} (recherche globale, SPRINT-21). */
    List<Fermier> findByNomContainingIgnoreCaseOrderByNomAsc(String motif, Pageable pagination);
}
