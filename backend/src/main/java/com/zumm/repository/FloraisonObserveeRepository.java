package com.zumm.repository;

import com.zumm.domain.FloraisonObservee;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Floraisons observees (SPRINT-32, lot H). Restreint au tenant. */
public interface FloraisonObserveeRepository extends JpaRepository<FloraisonObservee, Long> {

    List<FloraisonObservee> findAllByOrderByAnneeDescDateDebutAsc();

    List<FloraisonObservee> findByRessource_Site_IdOrderByAnneeDescDateDebutAsc(Long siteId);

    /**
     * Une ressource ne fleurit qu'une fois par an.
     *
     * <p>La contrainte `uq_floraison_annee` le garantit ; ce finder permet de
     * MODIFIER l'observation de l'annee au lieu d'echouer en 409 quand
     * l'apiculteur complete le pic ou la fin, ce qu'il fait forcement plus tard.
     */
    Optional<FloraisonObservee> findByRessource_IdAndAnnee(Long ressourceId, Integer annee);
}
