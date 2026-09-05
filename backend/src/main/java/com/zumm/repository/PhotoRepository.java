package com.zumm.repository;

import com.zumm.domain.Photo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux photos (US-010/028, elargi aux SPRINT-21 et 28). Restreint au tenant
 * ({@code @TenantId} + RLS).
 *
 * <p>Une methode par cible plutot qu'une requete generique sur un couple
 * {@code (type, id)} : la cible est portee par cinq colonnes distinctes et non
 * par un discriminant, ce qui permet aux cles etrangeres composites de faire leur
 * travail. Le prix est une methode par cible ; le gain est qu'aucune photo ne
 * peut designer un objet inexistant.
 */
public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByVisiteIdOrderByIdAsc(Long visiteId);

    List<Photo> findByRucheIdOrderByIdAsc(Long rucheId);

    List<Photo> findBySiteIdOrderByIdAsc(Long siteId);

    List<Photo> findByReineIdOrderByIdAsc(Long suiviReineId);

    List<Photo> findByRecolteIdOrderByIdAsc(Long recolteId);

    /** Scans d'ordonnance attaches a un traitement (SPRINT-28). */
    List<Photo> findByTraitementIdOrderByIdAsc(Long traitementId);
}
