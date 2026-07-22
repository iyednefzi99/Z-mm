package com.zumm.repository;

import com.zumm.domain.Fermier;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces a l'entite {@link Fermier}. Les requetes sont automatiquement restreintes au
 * tenant courant : filtre applicatif Hibernate ({@code @TenantId}) double par la
 * politique RLS PostgreSQL. Aucun filtre {@code tenant_id} n'est donc a ecrire ici.
 */
public interface FermierRepository extends JpaRepository<Fermier, Long> {
}
