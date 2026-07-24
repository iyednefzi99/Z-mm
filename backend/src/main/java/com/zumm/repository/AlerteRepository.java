package com.zumm.repository;

import com.zumm.domain.Alerte;
import com.zumm.domain.TypeIndicateur;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux alertes de seuils (US-018). Restreint au tenant courant
 * (@TenantId + RLS). Une seule alerte ouverte par (ruche, indicateur).
 */
public interface AlerteRepository extends JpaRepository<Alerte, Long> {

    /** Alerte actuellement ouverte pour une ruche et un indicateur, s'il y en a une. */
    Optional<Alerte> findByRuche_IdAndTypeIndicateurAndOuverteTrue(Long rucheId, TypeIndicateur type);

    /** Alertes ouvertes, les plus recentes d'abord (tableau de bord / synthese). */
    List<Alerte> findByOuverteTrueOrderByOuverteLeDesc();
}
