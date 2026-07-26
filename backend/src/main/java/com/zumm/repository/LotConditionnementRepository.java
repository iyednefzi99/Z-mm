package com.zumm.repository;

import com.zumm.domain.LotConditionnement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lots de conditionnement (US-056). Restreint au tenant courant (@TenantId + RLS).
 */
public interface LotConditionnementRepository extends JpaRepository<LotConditionnement, Long> {

    /**
     * Lots recents, composition chargee.
     *
     * <p>{@code @EntityGraph} et non un {@code findAll()} nu : la vue liste affiche
     * la mention d'origine de chaque lot, donc traverse la composition. Sans lui,
     * une page de 25 lots declenche 26 requetes.
     */
    @EntityGraph(attributePaths = "composition")
    List<LotConditionnement> findByOrderByDateConditionnementDescIdDesc();

    @EntityGraph(attributePaths = {"composition", "composition.recolte"})
    Optional<LotConditionnement> findByReference(String reference);

    boolean existsByReference(String reference);
}
