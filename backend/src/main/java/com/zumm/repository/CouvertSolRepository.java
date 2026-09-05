package com.zumm.repository;

import com.zumm.web.dto.SurfaceCouvert;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Occupation du sol : ingestion et intersections (SPRINT-32, lot H).
 *
 * <p><strong>En JDBC nu, comme {@link InvitationRepository}, et pour une raison
 * voisine</strong> : ce que ce depot fait n'est pas de la lecture d'entite. Une
 * colonne {@code GEOGRAPHY} ne se mappe pas en JPA sans Hibernate Spatial, et
 * l'ajouter ferait entrer une dependance entiere pour trois requetes que
 * PostGIS ecrit mieux lui-meme. Le depot suit d'ailleurs le patron deja pose par
 * {@code SiteRepository}, dont le voisinage et les grappes sont en SQL natif.
 *
 * <p><strong>Le filtre {@code tenant_id} est EXPLICITE partout</strong>, et il le
 * faut : une requete native echappe au discriminant Hibernate, et la RLS ne
 * protege pas si l'application se connecte en superutilisateur (durcissement de
 * la V3). On lit donc la variable de session directement — sur, quel que soit le
 * role.
 */
@Repository
public class CouvertSolRepository {

    private final JdbcTemplate jdbc;

    public CouvertSolRepository(DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    /**
     * Verse un polygone dans la couche.
     *
     * <p>{@code ST_GeomFromGeoJSON} valide la geometrie a l'insertion : un
     * polygone mal ferme ou une coordonnee hors du monde est refusee la, et non
     * six mois plus tard au milieu d'un calcul de surfaces.
     *
     * <p>{@code ST_Multi} normalise : la couche ne contient que des
     * multipolygones, et une intersection n'a pas a distinguer les deux formes.
     */
    public void inserer(String classe, String source, int millesime, String geoJson) {
        jdbc.update("""
                INSERT INTO couvert_sol (tenant_id, classe, source, millesime, geom)
                VALUES (current_setting('app.current_tenant', true), ?, ?, ?,
                        ST_Multi(ST_GeomFromGeoJSON(?))::geography)
                """, classe, source, millesime, geoJson);
    }

    /**
     * Surfaces par classe dans le rayon de butinage d'un rucher, en hectares.
     *
     * <p>Le calcul est fait par PostGIS sur des {@code geography} : les surfaces
     * sont donc geodesiques, en metres carres reels. Le faire en projection
     * plane donnerait des ecarts de plusieurs pourcents aux latitudes elevees —
     * et le produit vise aussi le Maghreb, ou l'erreur jouerait dans l'autre
     * sens.
     *
     * <p>L'intersection est bornee par le tampon AVANT d'etre mesuree : sans
     * cela, une parcelle qui deborde largement du rayon compterait en entier, et
     * « 60 % de cultures » designerait un departement plutot qu'un environnement
     * de butinage.
     */
    public List<SurfaceCouvert> surfacesAutour(long siteId, double rayonMetres, int millesime) {
        return jdbc.query("""
                WITH cercle AS (
                    SELECT ST_Buffer(geog, ?) AS zone
                    FROM site
                    WHERE id = ?
                      AND tenant_id = current_setting('app.current_tenant', true)
                )
                SELECT c.classe,
                       ROUND((SUM(ST_Area(ST_Intersection(c.geom::geometry,
                                                          cercle.zone::geometry)::geography))
                              / 10000.0)::numeric, 2) AS surface_ha
                FROM couvert_sol c, cercle
                WHERE c.tenant_id = current_setting('app.current_tenant', true)
                  AND c.millesime = ?
                  AND ST_Intersects(c.geom, cercle.zone)
                GROUP BY c.classe
                ORDER BY surface_ha DESC
                """,
                (rs, ligne) -> new SurfaceCouvert(rs.getString("classe"),
                        rs.getBigDecimal("surface_ha"), null),
                rayonMetres, siteId, millesime);
    }

    /**
     * Distance au polygone de culture le plus proche, en metres, ou {@code null}.
     *
     * <p>Sert la ligne « evaluation de l'exposition aux zones traitees », et il
     * faut dire ce qu'elle mesure vraiment : la distance a une CULTURE, pas a
     * une zone traitee. Aucune couche ouverte ne dit ce qui a ete epandu ni
     * quand. Presenter l'une pour l'autre serait une affirmation que rien ne
     * fonde.
     */
    public Double distanceCultureLaPlusProche(long siteId, int millesime) {
        List<Double> distances = jdbc.query("""
                SELECT MIN(ST_Distance(c.geom, s.geog)) AS distance
                FROM couvert_sol c, site s
                WHERE s.id = ?
                  AND s.tenant_id = current_setting('app.current_tenant', true)
                  AND c.tenant_id = current_setting('app.current_tenant', true)
                  AND c.millesime = ?
                  AND c.classe IN ('culture', 'verger', 'vigne')
                """, (rs, ligne) -> {
                    double valeur = rs.getDouble("distance");
                    return rs.wasNull() ? null : valeur;
                }, siteId, millesime);
        return distances.isEmpty() ? null : distances.get(0);
    }

    /** Millesimes presents dans la couche, du plus recent au plus ancien. */
    public List<Integer> millesimes() {
        return jdbc.queryForList("""
                SELECT DISTINCT millesime FROM couvert_sol
                WHERE tenant_id = current_setting('app.current_tenant', true)
                ORDER BY millesime DESC
                """, Integer.class);
    }

    /** Source declaree d'un millesime, ou {@code null} s'il n'existe pas. */
    public String source(int millesime) {
        List<String> sources = jdbc.queryForList("""
                SELECT DISTINCT source FROM couvert_sol
                WHERE tenant_id = current_setting('app.current_tenant', true)
                  AND millesime = ?
                ORDER BY source
                """, String.class, millesime);
        return sources.isEmpty() ? null : String.join(", ", sources);
    }

    /**
     * Retire un millesime entier.
     *
     * <p>Un millesime se REMPLACE, il ne se corrige pas : verser deux fois le
     * meme sans purger doublerait toutes les surfaces, et le total depasserait
     * celui du cercle sans que rien ne le signale.
     */
    public int purger(int millesime) {
        return jdbc.update("""
                DELETE FROM couvert_sol
                WHERE tenant_id = current_setting('app.current_tenant', true)
                  AND millesime = ?
                """, millesime);
    }
}
