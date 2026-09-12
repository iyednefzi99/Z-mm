package com.zumm.repository;
import com.zumm.domain.Tache;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
/**
 * Acces aux taches et rappels (US-031). Restreint au tenant courant
 * (@TenantId + RLS) : aucun filtre {@code tenant_id} n'est a ecrire ici.
 */
public interface TacheRepository extends JpaRepository<Tache, Long> {
    /**
     * Taches non faites dont l'echeance tombe au plus tard le {@code jour} donne (rappels).
     *
     * <p>Le graphe descend jusqu'au SITE de la ruche et au consommable, parce que
     * la feuille de chargement (SPRINT-33) groupe par rucher et somme par
     * consommable : sans lui, une tournee de quinze taches declencherait
     * quarante-cinq requetes de plus, une par association lue.
     */
    @EntityGraph(attributePaths = {"ruche", "ruche.site", "agent", "consommable"})
    List<Tache> findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(LocalDate jour);
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
    @EntityGraph(attributePaths = {"ruche", "agent", "consommable"})
    List<Tache> findAll();


    /**
     * Cette tache a-t-elle deja ete engendree ? (SPRINT-22)
     *
     * <p>Garde applicative du moteur de regles : elle evite l'aller-retour en
     * erreur pour le cas courant. L'index unique partiel reste le garde-fou —
     * deux executions concurrentes passeraient toutes deux ce test.
     */
    boolean existsByCleDeclencheur(String cleDeclencheur);

    /** Taches ouvertes, dans l'ordre ou l'on travaille : priorite puis echeance. */
    List<Tache> findByFaiteFalseOrderByPrioriteAscEcheanceAsc();
}
