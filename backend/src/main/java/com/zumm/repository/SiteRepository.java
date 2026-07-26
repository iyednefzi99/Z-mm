package com.zumm.repository;

import com.zumm.domain.Site;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acces a l'entite {@link Site}. Les requetes JPA sont automatiquement restreintes
 * au tenant courant : filtre applicatif Hibernate ({@code @TenantId}) double par la
 * politique RLS PostgreSQL.
 */
public interface SiteRepository extends JpaRepository<Site, Long> {

    /**
     * Identifiants des sites du tenant courant situes a moins de {@code rayonMetres}
     * du point donne (US-003, requete spatiale PostGIS). Renvoie des identifiants,
     * pas des entites : le rechargement via {@code findAllById} repasse par le
     * filtre {@code @TenantId}.
     *
     * <p>Le filtre {@code tenant_id} y est EXPLICITE : une requete native echappe au
     * discriminant Hibernate, et la RLS ne protege pas si l'application se connecte
     * en superutilisateur (cf. durcissement V3). On lit donc la variable de session
     * directement — sur, quel que soit le role.
     */
    @Query(value = """
            SELECT id FROM site
            WHERE tenant_id = current_setting('app.current_tenant', true)
              AND ST_DWithin(
                    geog,
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
                    :rayonMetres)
            ORDER BY geog <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            """, nativeQuery = true)
    List<Long> idsProches(@Param("latitude") double latitude,
                          @Param("longitude") double longitude,
                          @Param("rayonMetres") double rayonMetres);

    /**
     * Latitude moyenne des sites du tenant, ou {@code null} s'il n'en a aucun.
     *
     * <p>Sert a calibrer le rayon du regroupement (US-045) : {@code ST_ClusterDBSCAN}
     * travaille en unites de la projection, et Web Mercator dilate les distances d'un
     * facteur {@code 1/cos(latitude)}. Les ruchers d'une exploitation sont regionaux,
     * la latitude moyenne est donc une calibration suffisante.
     */
    @Query(value = """
            SELECT avg(latitude) FROM site
            WHERE tenant_id = current_setting('app.current_tenant', true)
            """, nativeQuery = true)
    BigDecimal latitudeMoyenne();

    /**
     * Affectation de chaque site du tenant a une grappe DBSCAN (US-045).
     *
     * <p>{@code eps} est exprime en metres de Web Mercator (cf. {@link #latitudeMoyenne()}),
     * pas en metres reels. Les sites isoles sortent avec une grappe {@code null} — le
     * service les remonte en grappes singleton plutot que de les perdre.
     *
     * <p>Le regroupement construit son propre index en memoire : l'index GiST
     * {@code ix_site_geog} n'intervient pas ici (il sert aux requetes de proximite).
     */
    @Query(value = """
            SELECT id AS site_id,
                   ST_ClusterDBSCAN(ST_Transform(geog::geometry, 3857),
                                    eps := :eps,
                                    minpoints := :minimumSites) OVER () AS grappe
            FROM site
            WHERE tenant_id = current_setting('app.current_tenant', true)
            """, nativeQuery = true)
    List<AffectationGrappe> affectationsGrappes(@Param("eps") double eps,
                                                @Param("minimumSites") int minimumSites);

    /**
     * Sites du tenant les plus proches du point donne, hors le site {@code exclu}
     * (US-046). Le tri par {@code <->} sur {@code geography} est un parcours de
     * l'index GiST : pas de produit cartesien, meme sur plusieurs centaines de sites.
     * La distance renvoyee est geodesique ({@code ST_Distance} sur {@code geography}),
     * pas une distance euclidienne en degres.
     */
    @Query(value = """
            SELECT id AS site_id,
                   ST_Distance(geog, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography)
                       AS distance_metres
            FROM site
            WHERE tenant_id = current_setting('app.current_tenant', true)
              AND id <> :exclu
            ORDER BY geog <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT :limite
            """, nativeQuery = true)
    List<VoisinProche> voisins(@Param("latitude") double latitude,
                               @Param("longitude") double longitude,
                               @Param("exclu") long exclu,
                               @Param("limite") int limite);

    /**
     * Distances geodesiques entre les sites demandes, une seule ligne par paire
     * (US-047). La matrice complete est reconstituee cote service : sur une tournee
     * d'une journee, le nombre de sites se compte en dizaines.
     */
    @Query(value = """
            SELECT a.id AS depart_id,
                   b.id AS arrivee_id,
                   ST_Distance(a.geog, b.geog) AS distance_metres
            FROM site a
            JOIN site b ON a.id < b.id
            WHERE a.tenant_id = current_setting('app.current_tenant', true)
              AND b.tenant_id = current_setting('app.current_tenant', true)
              AND a.id IN (:ids) AND b.id IN (:ids)
            """, nativeQuery = true)
    List<PaireDistance> distancesEntre(@Param("ids") Collection<Long> ids);

    /** Ligne de {@link #affectationsGrappes}. */
    interface AffectationGrappe {
        Long getSiteId();

        /** Numero de grappe DBSCAN, {@code null} pour un site isole. */
        Integer getGrappe();
    }

    /** Ligne de {@link #voisins}. */
    interface VoisinProche {
        Long getSiteId();

        Double getDistanceMetres();
    }

    /** Ligne de {@link #distancesEntre}. */
    interface PaireDistance {
        Long getDepartId();

        Long getArriveeId();

        Double getDistanceMetres();
    }

    /**
     * Listage complet, associations chargees en une seule requete (SPRINT-14).
     *
     * <p>Sans ce graphe, chaque ligne rendue declenchait une requete de plus pour
     * lire le libelle de son parent : le fameux « N+1 ». Invisible sur les
     * dizaines de lignes d'une demonstration, il devient le poste de cout
     * dominant sur un parc reel — 500 ruches, c'est 501 aller-retours la ou un
     * seul suffit.
     */
    @Override
    @EntityGraph(attributePaths = "ferme")
    List<Site> findAll();
}
