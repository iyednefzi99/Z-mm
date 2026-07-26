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

    /** Une ligne d'agregat de poids par ruche. */
    interface AgregatPoids {
        Long getRucheId();

        java.math.BigDecimal getMinimum();

        java.math.BigDecimal getMaximum();

        java.math.BigDecimal getActuel();

        long getNombre();
    }

    /**
     * Agregat du poids par ruche, calcule EN BASE (SPRINT-17).
     *
     * <p>Auparavant, le tableau de bord chargeait TOUTES les mesures de poids du
     * tenant pour les reduire en memoire. A raison d'un releve par quart d'heure,
     * une exploitation de 500 ruches en accumule des dizaines de millions par an :
     * la seule lecture aurait sature le tas avant meme d'agreger.
     *
     * <p>{@code last(valeur, instant)} est une agregation TimescaleDB, et c'est
     * exactement ce pour quoi l'extension a ete retenue (ADR-002) : obtenir la
     * DERNIERE valeur d'une serie sans la trier entierement. L'ecrire en SQL
     * standard demanderait une fonction de fenetrage ou une jointure laterale,
     * l'une comme l'autre plus couteuses.
     *
     * <p>Requete NATIVE et non JPQL : ni {@code last()} ni une projection
     * d'agregats multiples ne s'expriment en JPQL. La politique RLS et la portee
     * d'agent s'appliquent malgre tout — elles vivent dans la base, pas dans
     * Hibernate. C'est precisement l'interet de les y avoir mises.
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT ruche_id AS \"rucheId\","
            + " min(valeur) AS \"minimum\","
            + " max(valeur) AS \"maximum\","
            + " last(valeur, instant) AS \"actuel\","
            + " count(*) AS \"nombre\""
            + " FROM mesure WHERE type_indicateur = :type GROUP BY ruche_id",
            nativeQuery = true)
    List<AgregatPoids> agregatPoids(String type);
}
