package com.zumm.repository;

import com.zumm.domain.Ferme;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces a l'entite {@link Ferme}. Les requetes sont automatiquement restreintes au
 * tenant courant : filtre applicatif Hibernate ({@code @TenantId}) double par la
 * politique RLS PostgreSQL. Aucun filtre {@code tenant_id} n'est donc a ecrire ici.
 */
public interface FermeRepository extends JpaRepository<Ferme, Long> {
}
