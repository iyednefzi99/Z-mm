package com.zumm.repository;

import com.zumm.domain.ProduitTraitement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Lecture du referentiel de produits de traitement (SPRINT-28).
 *
 * <p>Meme regime que {@link PointObservationRepository} : lecture seule, et
 * tenue par PostgreSQL — la V28 retire le DML au role applicatif.
 */
public interface ProduitTraitementRepository extends JpaRepository<ProduitTraitement, String> {

    List<ProduitTraitement> findAllByOrderByNomAsc();
}
