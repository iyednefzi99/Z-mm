package com.zumm.repository;

import com.zumm.web.dto.ZoneTraiteeReponse;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Zones traitees declarees : ecriture et voisinage (SPRINT-33, lot K).
 *
 * <p><strong>En JDBC nu, pour la meme raison que {@link CouvertSolRepository}</strong> :
 * une colonne {@code GEOGRAPHY} ne se mappe pas en JPA sans Hibernate Spatial, et
 * l'ajouter ferait entrer une dependance entiere pour quatre requetes que PostGIS
 * ecrit mieux lui-meme.
 *
 * <p><strong>Le filtre {@code tenant_id} est EXPLICITE partout.</strong> Une
 * requete native echappe au discriminant Hibernate, et la RLS ne protege ni le
 * proprietaire de la table ni un superutilisateur : s'y fier seule ferait
 * dependre le cloisonnement du role de connexion.
 */
@Repository
public class ZoneTraiteeRepository {

    private static final RowMapper<ZoneTraiteeReponse> ZONE = (rs, ligne) ->
            new ZoneTraiteeReponse(
                    rs.getLong("id"),
                    rs.getObject("date_traitement", LocalDate.class),
                    rs.getString("substance"),
                    rs.getString("origine"),
                    (Integer) rs.getObject("delai_rentree_h"),
                    rs.getString("note"),
                    rs.getBigDecimal("surface_ha"));

    private final JdbcTemplate jdbc;

    public ZoneTraiteeRepository(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /**
     * Declare une zone.
     *
     * <p>{@code ST_GeomFromGeoJSON} valide la geometrie a l'insertion, et
     * {@code ST_Multi} normalise : la couche ne contient que des multipolygones,
     * et une intersection n'a pas a distinguer les deux formes. Meme traitement
     * que le versement d'occupation du sol.
     */
    public Long inserer(String geoJson, LocalDate date, String substance, String origine,
            Integer delaiRentreeH, String note) {
        KeyHolder cle = new GeneratedKeyHolder();
        jdbc.update(connexion -> {
            var requete = connexion.prepareStatement("""
                    INSERT INTO zone_traitee (tenant_id, geom, date_traitement, substance,
                                              origine, delai_rentree_h, note)
                    VALUES (current_setting('app.current_tenant', true),
                            ST_Multi(ST_GeomFromGeoJSON(?))::geography, ?, ?, ?, ?, ?)
                    """, new String[] { "id" });
            requete.setString(1, geoJson);
            requete.setObject(2, date);
            requete.setString(3, substance);
            requete.setString(4, origine);
            requete.setObject(5, delaiRentreeH, java.sql.Types.INTEGER);
            requete.setString(6, note);
            return requete;
        }, cle);
        Number id = cle.getKey();
        return id == null ? null : id.longValue();
    }

    /** Toutes les declarations de l'exploitation, de la plus recente a la plus ancienne. */
    public List<ZoneTraiteeReponse> lister() {
        return jdbc.query("""
                SELECT id, date_traitement, substance, origine, delai_rentree_h, note,
                       ROUND((ST_Area(geom) / 10000.0)::numeric, 2) AS surface_ha
                FROM zone_traitee
                WHERE tenant_id = current_setting('app.current_tenant', true)
                ORDER BY date_traitement DESC, id DESC
                """, ZONE);
    }

    /**
     * Declarations qui touchent le rayon de butinage d'un rucher.
     *
     * <p>Bornees au rayon, et non a une distance arbitraire : ce qui compte est
     * l'endroit ou les abeilles vont, pas ce qui est proche a vol d'oiseau.
     */
    public List<ZoneTraiteeReponse> autour(long siteId, double rayonMetres) {
        return jdbc.query("""
                WITH cercle AS (
                    SELECT ST_Buffer(geog, ?) AS zone
                    FROM site
                    WHERE id = ?
                      AND tenant_id = current_setting('app.current_tenant', true)
                )
                SELECT z.id, z.date_traitement, z.substance, z.origine, z.delai_rentree_h,
                       z.note,
                       ROUND((ST_Area(ST_Intersection(z.geom::geometry,
                                                      cercle.zone::geometry)::geography)
                              / 10000.0)::numeric, 2) AS surface_ha
                FROM zone_traitee z, cercle
                WHERE z.tenant_id = current_setting('app.current_tenant', true)
                  AND ST_Intersects(z.geom, cercle.zone)
                ORDER BY z.date_traitement DESC, z.id DESC
                """, ZONE, rayonMetres, siteId);
    }

    /** Distance a la zone declaree la plus proche, en metres, ou {@code null}. */
    public Double distanceMin(long siteId) {
        List<Double> distances = jdbc.query("""
                SELECT MIN(ST_Distance(z.geom, s.geog)) AS distance
                FROM zone_traitee z, site s
                WHERE s.id = ?
                  AND s.tenant_id = current_setting('app.current_tenant', true)
                  AND z.tenant_id = current_setting('app.current_tenant', true)
                """, (rs, ligne) -> {
                    double valeur = rs.getDouble("distance");
                    return rs.wasNull() ? null : valeur;
                }, siteId);
        return distances.isEmpty() ? null : distances.get(0);
    }

    /**
     * Ruchers dont le rayon touche une zone declaree recemment.
     *
     * <p>Sert la regle qui engendre la tache de vigilance. Une ligne par rucher :
     * une tache par ZONE en produirait dix pour un traitement declare parcelle
     * par parcelle, et la liste ne serait plus lue — meme raisonnement que la
     * regle de verification terrain.
     */
    public List<ExpositionBrute> ruchersExposes(int rayonDefautKm, LocalDate depuis) {
        return jdbc.query("""
                SELECT s.id, s.nom, COUNT(z.id) AS zones, MAX(z.date_traitement) AS derniere,
                       ROUND(MIN(ST_Distance(z.geom, s.geog))::numeric, 0) AS distance_m
                FROM site s
                JOIN zone_traitee z
                  ON z.tenant_id = s.tenant_id
                 AND z.date_traitement >= ?
                 AND ST_Intersects(z.geom,
                        ST_Buffer(s.geog, COALESCE(s.rayon_butinage_km, ?) * 1000))
                WHERE s.tenant_id = current_setting('app.current_tenant', true)
                  AND s.geog IS NOT NULL
                GROUP BY s.id, s.nom
                ORDER BY s.id
                """,
                (rs, ligne) -> new ExpositionBrute(
                        rs.getLong("id"), rs.getString("nom"), rs.getInt("zones"),
                        rs.getObject("derniere", LocalDate.class),
                        rs.getBigDecimal("distance_m")),
                depuis, rayonDefautKm);
    }

    public int supprimer(long id) {
        return jdbc.update("""
                DELETE FROM zone_traitee
                WHERE id = ?
                  AND tenant_id = current_setting('app.current_tenant', true)
                """, id);
    }

    /**
     * Un rucher expose, tel que la regle en a besoin.
     *
     * <p>Enregistrement local au depot : il ne sort jamais par l'API.
     */
    public record ExpositionBrute(Long siteId, String siteNom, int zones,
            LocalDate derniere, java.math.BigDecimal distanceM) {
    }
}
