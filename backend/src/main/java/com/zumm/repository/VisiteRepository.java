package com.zumm.repository;

import com.zumm.domain.Visite;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces a l'entite {@link Visite} (SPRINT-03). Restreint au tenant (@TenantId + RLS). */
public interface VisiteRepository extends JpaRepository<Visite, Long> {

    /** Visites du tenant dont la date tombe dans [debut, fin] (US-012, calendrier). */
    List<Visite> findByDateVisiteBetweenOrderByDateVisiteAsc(LocalDate debut, LocalDate fin);

    /** Toutes les visites, plus anciennes d'abord : la derniere vue est la plus recente (US-014). */
    List<Visite> findAllByOrderByDateVisiteAsc();
}
