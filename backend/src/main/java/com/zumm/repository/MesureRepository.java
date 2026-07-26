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
            + " FROM mesure WHERE type_indicateur = :type"
            // Filtre de tenant EXPLICITE : une requete NATIVE echappe au
            // discriminant `@TenantId` d'Hibernate, qui ne reecrit que le JPQL.
            // En production la RLS suffirait ; ici on tient les DEUX barrieres,
            // comme partout ailleurs — et cela protege aussi les deploiements ou
            // l'application se connecterait avec un role privilegie.
            + " AND tenant_id = current_setting('app.current_tenant', true)"
            + " GROUP BY ruche_id",
            nativeQuery = true)
    List<AgregatPoids> agregatPoids(String type);

    /** Un compartiment journalier d'un indicateur, pour une ruche. */
    interface CompartimentJournalier {
        java.time.Instant getJour();

        java.math.BigDecimal getMoyenne();

        java.math.BigDecimal getMinimum();

        java.math.BigDecimal getMaximum();

        long getNombre();
    }

    /**
     * Serie journaliere d'un indicateur, agregee EN BASE (SPRINT-18).
     *
     * <p>Le probleme resolu : la courbe de capteur lisait la serie BRUTE. A un
     * releve par quart d'heure, trois ans d'historique font ~105 000 points pour
     * UNE ruche — le serveur les serialise tous, le reseau les transporte tous, et
     * le navigateur en jette 99 % : un graphique de 640 pixels de large n'en
     * montrera jamais plus que sa largeur.
     *
     * <p><strong>Pourquoi une agregation a la demande et non un agregat continu.</strong>
     * L'intention initiale etait d'entretenir un {@code CONTINUOUS AGGREGATE}
     * TimescaleDB. PostgreSQL le refuse :
     *
     * <pre>ERROR: cannot create continuous aggregate on hypertable with row security</pre>
     *
     * <p>C'est la SECONDE manifestation du conflit deja tranche par l'ADR-008 —
     * apres la compression. La RLS et les fonctionnalites avancees de TimescaleDB
     * s'excluent, et Zumm garde la RLS. L'ADR-008 a ete generalise en consequence.
     *
     * <p>Ce que l'agregation a la demande conserve du benefice recherche : le
     * volume transporte tombe de ~105 000 points a ~1 100, le calcul se fait la ou
     * sont les donnees, et la RLS comme la portee d'agent continuent de
     * s'appliquer. Ce qu'elle perd : le resultat est recalcule a chaque appel.
     * L'index {@code (tenant_id, ruche_id, type_indicateur, instant DESC)} pose au
     * SPRINT-14 rend ce cout acceptable sur une plage bornee.
     */
    @org.springframework.data.jpa.repository.Query(value =
            "SELECT time_bucket(INTERVAL '1 day', instant) AS \"jour\","
            + " avg(valeur) AS \"moyenne\","
            + " min(valeur) AS \"minimum\","
            + " max(valeur) AS \"maximum\","
            + " count(*) AS \"nombre\""
            + " FROM mesure"
            + " WHERE ruche_id = :rucheId AND type_indicateur = :type"
            // Meme raison que ci-dessus : requete native, donc filtre explicite.
            + " AND tenant_id = current_setting('app.current_tenant', true)"
            + " GROUP BY jour ORDER BY jour",
            nativeQuery = true)
    List<CompartimentJournalier> serieJournaliere(Long rucheId, String type);
}
