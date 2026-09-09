package com.zumm.repository;

import com.zumm.web.dto.ParcelleCouvert;
import com.zumm.web.dto.SurfaceCouvert;
import java.time.LocalDate;
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
                SELECT COALESCE(c.classe_constatee, c.classe) AS classe,
                       ROUND((SUM(ST_Area(ST_Intersection(c.geom::geometry,
                                                          cercle.zone::geometry)::geography))
                              / 10000.0)::numeric, 2) AS surface_ha
                FROM couvert_sol c, cercle
                WHERE c.tenant_id = current_setting('app.current_tenant', true)
                  AND c.millesime = ?
                  AND ST_Intersects(c.geom, cercle.zone)
                GROUP BY COALESCE(c.classe_constatee, c.classe)
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
     * fonde — c'est {@link ZoneTraiteeRepository} (SPRINT-33) qui repond a la
     * seconde question, sur des declarations et non sur une couche.
     */
    public Double distanceCultureLaPlusProche(long siteId, int millesime) {
        List<Double> distances = jdbc.query("""
                SELECT MIN(ST_Distance(c.geom, s.geog)) AS distance
                FROM couvert_sol c, site s
                WHERE s.id = ?
                  AND s.tenant_id = current_setting('app.current_tenant', true)
                  AND c.tenant_id = current_setting('app.current_tenant', true)
                  AND c.millesime = ?
                  AND COALESCE(c.classe_constatee, c.classe)
                      IN ('culture', 'verger', 'vigne')
                """, (rs, ligne) -> {
                    double valeur = rs.getDouble("distance");
                    return rs.wasNull() ? null : valeur;
                }, siteId, millesime);
        return distances.isEmpty() ? null : distances.get(0);
    }

    // ─── Verification terrain (SPRINT-33, lot K) ────────────────────────────

    /**
     * Marque une parcelle « a confirmer », ou leve le doute.
     *
     * <p>Le doute se POSE, il ne se deduit pas. Une regle qui marquerait d'office
     * « toute culture de plus de deux ans » fabriquerait une charge de travail
     * que personne n'a demandee, sur des parcelles que personne ne soupconne.
     *
     * @return le nombre de lignes touchees — zero si la parcelle n'existe pas
     *         dans l'exploitation, ce que le service traduit en 404
     */
    public int marquerAConfirmer(long id, boolean aConfirmer) {
        return jdbc.update("""
                UPDATE couvert_sol SET a_confirmer = ?
                WHERE id = ?
                  AND tenant_id = current_setting('app.current_tenant', true)
                """, aConfirmer, id);
    }

    /**
     * Enregistre ce que le terrain a montre.
     *
     * <p><strong>{@code classe} n'est jamais touchee.</strong> L'ecraser
     * detruirait exactement ce que le ground truthing etablit — que la couche se
     * trompait —, et la fiabilite d'un millesime ne se mesurerait plus. Le
     * constat s'ecrit a cote, et les lectures le prennent des qu'il existe.
     *
     * <p>Le doute retombe du meme geste : une parcelle constatee n'est plus a
     * confirmer, sans quoi la regle la reproposerait indefiniment.
     */
    public int enregistrerConstat(long id, String classeConstatee, LocalDate constateLe,
            String note) {
        return jdbc.update("""
                UPDATE couvert_sol
                   SET classe_constatee = ?, constate_le = ?, constat_note = ?,
                       a_confirmer = false
                WHERE id = ?
                  AND tenant_id = current_setting('app.current_tenant', true)
                """, classeConstatee, constateLe, note, id);
    }

    /**
     * Parcelles en attente de verification autour d'un rucher, ou toutes.
     *
     * <p>Sans {@code siteId}, la couche entiere : c'est la vue de l'exploitant
     * qui prepare sa saison. Avec, ce qu'il faut regarder en arrivant sur CE
     * rucher — bornees au rayon de butinage, sans quoi la liste porterait sur un
     * departement.
     */
    public List<ParcelleCouvert> parcelles(Long siteId, double rayonMetres, boolean enAttente) {
        String filtreAttente = enAttente
                ? " AND c.a_confirmer AND c.classe_constatee IS NULL" : "";
        if (siteId == null) {
            return jdbc.query("""
                    SELECT c.id, c.classe, c.classe_constatee, c.source, c.millesime,
                           c.a_confirmer, c.constate_le, c.constat_note,
                           ROUND((ST_Area(c.geom) / 10000.0)::numeric, 2) AS surface_ha
                    FROM couvert_sol c
                    WHERE c.tenant_id = current_setting('app.current_tenant', true)
                    """ + filtreAttente + """
                     ORDER BY c.a_confirmer DESC, c.millesime DESC, c.id
                    """, PARCELLE);
        }
        return jdbc.query("""
                WITH cercle AS (
                    SELECT ST_Buffer(geog, ?) AS zone
                    FROM site
                    WHERE id = ?
                      AND tenant_id = current_setting('app.current_tenant', true)
                )
                SELECT c.id, c.classe, c.classe_constatee, c.source, c.millesime,
                       c.a_confirmer, c.constate_le, c.constat_note,
                       ROUND((ST_Area(ST_Intersection(c.geom::geometry,
                                                      cercle.zone::geometry)::geography)
                              / 10000.0)::numeric, 2) AS surface_ha
                FROM couvert_sol c, cercle
                WHERE c.tenant_id = current_setting('app.current_tenant', true)
                  AND ST_Intersects(c.geom, cercle.zone)
                """ + filtreAttente + """
                 ORDER BY c.a_confirmer DESC, c.millesime DESC, c.id
                """, PARCELLE, rayonMetres, siteId);
    }

    private static final org.springframework.jdbc.core.RowMapper<ParcelleCouvert> PARCELLE =
            (rs, ligne) -> new ParcelleCouvert(
                    rs.getLong("id"),
                    rs.getString("classe"),
                    rs.getString("classe_constatee"),
                    rs.getString("source"),
                    rs.getInt("millesime"),
                    rs.getBoolean("a_confirmer"),
                    rs.getObject("constate_le", LocalDate.class),
                    rs.getString("constat_note"),
                    rs.getBigDecimal("surface_ha"));

    /**
     * Ruchers ayant au moins une parcelle a confirmer dans leur rayon.
     *
     * <p>Rend une ligne par rucher — et c'est ce qui rend la regle de
     * verification tenable. Une tache par PARCELLE produirait quarante entrees
     * pour un rucher de plaine, et une liste de quarante lignes identiques n'est
     * plus lue. Le rayon est celui du rucher (V31), avec le defaut de
     * configuration en repli.
     */
    public List<RucherAVerifier> ruchersAVerifier(int rayonDefautKm) {
        return jdbc.query("""
                SELECT s.id, s.nom, COUNT(c.id) AS parcelles
                FROM site s
                JOIN couvert_sol c
                  ON c.tenant_id = s.tenant_id
                 AND c.a_confirmer
                 AND c.classe_constatee IS NULL
                 AND ST_Intersects(c.geom,
                        ST_Buffer(s.geog, COALESCE(s.rayon_butinage_km, ?) * 1000))
                WHERE s.tenant_id = current_setting('app.current_tenant', true)
                  AND s.geog IS NOT NULL
                GROUP BY s.id, s.nom
                ORDER BY s.id
                """,
                (rs, ligne) -> new RucherAVerifier(
                        rs.getLong("id"), rs.getString("nom"), rs.getInt("parcelles")),
                rayonDefautKm);
    }

    /**
     * Un rucher et le nombre de parcelles que le terrain doit trancher.
     *
     * <p>Enregistrement local au depot : il ne sort jamais par l'API, seule la
     * regle de verification le lit. En faire un DTO du contrat obligerait a le
     * documenter dans OpenAPI pour un usage interne.
     */
    public record RucherAVerifier(Long siteId, String siteNom, int parcelles) {
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
