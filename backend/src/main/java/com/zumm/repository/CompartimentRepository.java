package com.zumm.repository;

import com.zumm.domain.Compartiment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Corps et hausses d'une ruche (SPRINT-02). Le depot n'en avait pas besoin
 * jusqu'ici : la composition se lit par la ruche qui la porte. Le poids par
 * hausse (SPRINT-26) demande d'atteindre un compartiment SEUL, depuis son
 * identifiant, sans savoir a quelle ruche il appartient.
 */
public interface CompartimentRepository extends JpaRepository<Compartiment, Long> {

    List<Compartiment> findByRuche_IdOrderByIdAsc(Long rucheId);
}
