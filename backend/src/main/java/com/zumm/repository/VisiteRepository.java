package com.zumm.repository;

import com.zumm.domain.Visite;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acces a l'entite {@link Visite} (SPRINT-03). Restreint au tenant (@TenantId + RLS). */
public interface VisiteRepository extends JpaRepository<Visite, Long> {

    /** Visites du tenant dont la date tombe dans [debut, fin] (US-012, calendrier). */
    List<Visite> findByDateVisiteBetweenOrderByDateVisiteAsc(LocalDate debut, LocalDate fin);

    /** Toutes les visites, plus anciennes d'abord : la derniere vue est la plus recente (US-014). */
    List<Visite> findAllByOrderByDateVisiteAsc();

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
    @EntityGraph(attributePaths = {"ruche", "agent"})
    List<Visite> findAll();

    /** Productivite moyenne relevee, par ruche. */
    interface ProductiviteRuche {
        Long getRucheId();

        Double getMoyenne();
    }

    /**
     * Productivite moyenne par ruche, calculee EN BASE (SPRINT-17).
     *
     * <p>Remplace un {@code findAll()} suivi d'une somme en memoire. Calculer une
     * moyenne est le travail meme d'un SGBD ; lui faire transferer toutes les
     * lignes pour la calculer soi-meme n'apportait rien et coutait tout.
     */
    @Query("SELECT v.ruche.id AS rucheId, avg(v.productivite) AS moyenne FROM Visite v"
            + " WHERE v.productivite IS NOT NULL GROUP BY v.ruche.id")
    List<ProductiviteRuche> productiviteMoyenneParRuche();

    /**
     * Derniere visite de chaque ruche (SPRINT-17).
     *
     * <p>{@code DISTINCT ON} est propre a PostgreSQL et rend exactement ce qu'on
     * cherche : une ligne par ruche, la plus recente. La variante portable —
     * fenetrage puis filtre sur le rang — serait plus longue a lire pour le meme
     * resultat, et le projet assume deja PostgreSQL de bout en bout (PostGIS, RLS,
     * TimescaleDB).
     *
     * <p>Le depart : le tableau des alertes sanitaires lisait TOUTES les visites de
     * l'exploitation pour n'en garder qu'une par ruche.
     */
    @Query(value = "SELECT DISTINCT ON (ruche_id) * FROM visite"
            // Filtre de tenant EXPLICITE : une requete native echappe au
            // discriminant `@TenantId`, qui ne reecrit que le JPQL.
            + " WHERE tenant_id = current_setting('app.current_tenant', true)"
            + " ORDER BY ruche_id, date_visite DESC, id DESC", nativeQuery = true)
    List<Visite> dernieresVisitesParRuche();

    /** Nombre de visites par motif. */
    interface CompteParRaison {
        /**
         * Le motif comme ENUMERATION, et non comme chaine.
         *
         * <p>Le declarer {@code String} paraissait plus simple et introduisait un
         * defaut silencieux : Spring convertissait alors l'enumeration par son
         * {@code toString()}, c'est-a-dire le nom de la constante Java
         * (« RECOLTE »), la ou l'API a toujours expose la valeur en base
         * (« recolte »). Les cles de la synthese changeaient donc de casse sans
         * qu'aucune signature ne bouge. La conversion appartient a
         * {@code enBase()} : c'est le seul endroit qui la connaisse.
         */
        com.zumm.domain.RaisonVisite getRaison();

        long getNombre();
    }

    /**
     * Repartition des visites par motif, comptee EN BASE (SPRINT-18).
     *
     * <p>La synthese parcourait toutes les visites de l'exploitation pour en
     * construire une carte de comptage. Un {@code GROUP BY} rend le meme resultat
     * sans transferer une seule ligne de detail.
     */
    @Query("SELECT v.raison AS raison, count(v) AS nombre FROM Visite v GROUP BY v.raison")
    List<CompteParRaison> compterParRaison();

    /**
     * Productivite moyenne sur l'ensemble des visites du tenant (SPRINT-18).
     *
     * <p>{@code null} quand aucune visite n'en porte : c'est une absence de
     * donnee, pas un zero, et l'afficher comme zero laisserait croire a une
     * production nulle.
     */
    @Query("SELECT avg(v.productivite) FROM Visite v WHERE v.productivite IS NOT NULL")
    Double productiviteMoyenneGlobale();
}
