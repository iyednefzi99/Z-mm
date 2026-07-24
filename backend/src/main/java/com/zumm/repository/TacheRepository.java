package com.zumm.repository;

import com.zumm.domain.Tache;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acces aux taches et rappels (US-031). Restreint au tenant courant
 * (@TenantId + RLS) : aucun filtre {@code tenant_id} n'est a ecrire ici.
 */
public interface TacheRepository extends JpaRepository<Tache, Long> {

    /** Taches non faites dont l'echeance tombe au plus tard le {@code jour} donne (rappels). */
    List<Tache> findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(LocalDate jour);
}
