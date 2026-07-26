package com.zumm.repository;

import com.zumm.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces a l'entite {@link Agent}. Les requetes sont automatiquement restreintes au
 * tenant courant : filtre applicatif Hibernate ({@code @TenantId}) double par la
 * politique RLS PostgreSQL. Aucun filtre {@code tenant_id} n'est donc a ecrire ici.
 */
public interface AgentRepository extends JpaRepository<Agent, Long> {

    /** Agent lie a ce compte, cle stable (US-057). */
    java.util.Optional<Agent> findBySujetOidc(String sujetOidc);

    /**
     * Agent portant ce courriel, insensible a la casse.
     *
     * <p>Sert UNIQUEMENT a la premiere liaison d'un compte : une fois le sujet
     * inscrit, c'est lui qui fait foi. Insensible a la casse parce qu'un
     * fournisseur d'identite normalise rarement de la meme facon qu'une saisie
     * manuelle.
     */
    java.util.Optional<Agent> findByEmailIgnoreCase(String email);
}
