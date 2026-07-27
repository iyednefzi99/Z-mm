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

    /**
     * Masse totale recoltee, en kilogrammes (US-015, valorisation du ROI).
     *
     * <p>Somme calculee EN BASE : rapatrier les recoltes pour les additionner en
     * Java est le defaut corrige au SPRINT-17 partout ailleurs.
     *
     * <p>JPQL et non SQL natif : le discriminant {@code @TenantId} d'Hibernate ne
     * reecrit que le JPQL (piege du SPRINT-18). En natif, il faudrait un filtre de
     * tenant explicite pour garder les deux barrieres.
     */
    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(r.quantiteKg), 0) from Recolte r")
    java.math.BigDecimal quantiteTotaleRecoltee();

    /** Masse recoltee sur une ruche donnee, en kilogrammes (US-026). */
    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(r.quantiteKg), 0) from Recolte r where r.ruche.id = :rucheId")
    java.math.BigDecimal quantiteRecolteeParRuche(Long rucheId);
}
