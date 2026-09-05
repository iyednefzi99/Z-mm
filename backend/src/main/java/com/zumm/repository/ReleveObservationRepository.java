package com.zumm.repository;

import com.zumm.domain.ReleveObservation;
import com.zumm.web.dto.StatistiquePoint;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Releves de points d'observation (SPRINT-28). Restreint au tenant. */
public interface ReleveObservationRepository
        extends JpaRepository<ReleveObservation, ReleveObservation.Cle> {

    List<ReleveObservation> findByIdVisiteIdOrderByIdPointCodeAsc(Long visiteId);

    /**
     * Ce que chaque point a donne sur une periode.
     *
     * <p>Une jointure et non trois requetes : le referentiel fournit le libelle
     * et le type, la visite fournit la date. Le comptage se fait en base parce
     * qu'un parc de quelques centaines de ruches produit des dizaines de
     * milliers de releves par saison — les remonter pour les compter en Java
     * serait la meme erreur que celle evitee pour {@code date_retrait} (V19).
     *
     * <p>La RLS s'applique aux deux tables d'exploitation traversees
     * ({@code releve_observation} et {@code visite}) : une statistique ne peut
     * pas franchir la frontiere du tenant, meme par agregat.
     */
    @Query("""
            SELECT new com.zumm.web.dto.StatistiquePoint(
                p.code, p.libelle, p.categorie, p.typeValeur,
                COUNT(r),
                SUM(CASE WHEN r.valeurBool = TRUE THEN 1L ELSE 0L END),
                AVG(r.valeurEchelle))
            FROM ReleveObservation r, Visite v, PointObservation p
            WHERE v.id = r.id.visiteId
              AND p.code = r.id.pointCode
              AND v.dateVisite BETWEEN :depuis AND :jusqu
            GROUP BY p.code, p.libelle, p.categorie, p.typeValeur, p.ordre
            ORDER BY p.ordre
            """)
    List<StatistiquePoint> statistiques(@Param("depuis") LocalDate depuis,
            @Param("jusqu") LocalDate jusqu);
}
