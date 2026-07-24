package com.zumm.repository;

import com.zumm.domain.Mesure;
import com.zumm.domain.MesureId;
import com.zumm.domain.TypeIndicateur;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acces aux mesures (US-016), cle composite. Restreint au tenant (@TenantId + RLS). */
public interface MesureRepository extends JpaRepository<Mesure, MesureId> {

    /**
     * Toutes les mesures d'un indicateur, triees par ruche puis instant croissant.
     * Le tri par instant croissant fait de la derniere valeur vue, pour une ruche
     * donnee, la mesure la plus recente (agregation en memoire, US-013/US-034).
     */
    List<Mesure> findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur type);

    /** Mesures d'une ruche pour un indicateur, de la plus ancienne a la plus recente (US-034). */
    List<Mesure> findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(Long rucheId, TypeIndicateur type);
}
