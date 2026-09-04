package com.zumm.repository;

import com.zumm.domain.Transport;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plans de transhumance (SPRINT-21). Restreint au tenant ET a la portee de
 * l'agent (RLS, V21) : un transport porte une destination, donc une position a
 * venir.
 */
public interface TransportRepository extends JpaRepository<Transport, Long> {

    List<Transport> findBySite_IdOrderByDatePrevueDescIdDesc(Long siteId);

    /** Ce qui reste a deplacer : la question du quotidien, servie par un index partiel. */
    List<Transport> findByStatutOrderByDatePrevueAscIdAsc(String statut);

    List<Transport> findByStatutAndDatePrevueBetweenOrderByDatePrevueAscIdAsc(
            String statut, LocalDate debut, LocalDate fin);
}
