package com.zumm.repository;

import com.zumm.domain.PointObservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lecture du referentiel ferme des points d'observation (SPRINT-28).
 *
 * <p>Aucune methode d'ecriture n'est exposee, et ce n'est pas seulement une
 * convention : la migration V28 retire {@code INSERT}, {@code UPDATE} et
 * {@code DELETE} au role applicatif sur cette table. Ce referentiel ne s'ecrit
 * que par migration.
 */
public interface PointObservationRepository extends JpaRepository<PointObservation, String> {

    /** Les points dans l'ordre d'affichage voulu par le referentiel. */
    List<PointObservation> findAllByOrderByOrdreAsc();
}
