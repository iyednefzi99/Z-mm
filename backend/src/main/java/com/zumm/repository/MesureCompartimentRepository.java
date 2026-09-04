package com.zumm.repository;

import com.zumm.domain.MesureCompartiment;
import com.zumm.domain.MesureCompartimentId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Serie de poids par compartiment (SPRINT-26). Restreinte au tenant.
 */
public interface MesureCompartimentRepository
        extends JpaRepository<MesureCompartiment, MesureCompartimentId> {

    /** Serie d'un compartiment sur une fenetre, du plus ancien au plus recent. */
    List<MesureCompartiment> findByIdCompartimentIdAndIdInstantBetweenOrderByIdInstantAsc(
            Long compartimentId, Instant debut, Instant fin);

    /**
     * Dernier poids connu de chaque compartiment d'une ruche.
     *
     * <p>Requete unique plutot qu'une par hausse : l'ecran des capteurs affiche
     * la repartition d'un coup, et un N+1 sur une hypertable coute cher.
     */
    @Query("""
            select m from MesureCompartiment m
            where m.id.compartimentId in :compartiments
              and m.id.instant = (
                  select max(x.id.instant) from MesureCompartiment x
                  where x.id.compartimentId = m.id.compartimentId
                    and x.id.typeIndicateur = m.id.typeIndicateur)
            order by m.id.compartimentId asc
            """)
    List<MesureCompartiment> derniersParCompartiment(
            @Param("compartiments") List<Long> compartiments);
}
