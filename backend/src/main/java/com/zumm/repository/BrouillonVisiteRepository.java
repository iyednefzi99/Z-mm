package com.zumm.repository;

import com.zumm.domain.BrouillonVisite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Brouillons de visite (SPRINT-24). Restreint au tenant ET a l'agent : la
 * politique RLS de {@code V24} est la seule du schema qui n'ouvre PAS sur une
 * portee globale — une saisie en cours reste un travail prive tant qu'elle n'est
 * pas devenue une visite.
 */
public interface BrouillonVisiteRepository extends JpaRepository<BrouillonVisite, Long> {

    /** Mes brouillons, du plus recent au plus ancien : la seule liste affichee. */
    List<BrouillonVisite> findByAgent_IdOrderByMajLeDesc(Long agentId);

    /**
     * Le brouillon d'un agent sur une ruche, s'il existe.
     *
     * <p>L'index unique {@code uq_brouillon_agent_ruche} garantit qu'il n'y en a
     * jamais deux : deux brouillons ne repondraient a aucune question, ils
     * poseraient celle de savoir lequel reprendre.
     */
    Optional<BrouillonVisite> findByAgent_IdAndRuche_Id(Long agentId, Long rucheId);
}
