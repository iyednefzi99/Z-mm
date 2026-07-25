package com.zumm.web;

import com.zumm.configmetier.ConfigurationMetier;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Pagination des endpoints de liste (US-052, SPRINT-11).
 *
 * <p>Avant ce sprint, chaque {@code GET} de liste rendait la table entiere. Sur une
 * exploitation reelle, la liste des ruches, des mesures ou des visites croit sans
 * borne : le serveur serialise tout, le reseau transporte tout, le navigateur
 * garde tout en memoire.
 *
 * <h2>Choix de conception : le total est dans un en-tete</h2>
 *
 * <p>Le corps reste un <b>tableau JSON</b>, pagine ou non. L'alternative — envelopper
 * la liste dans un objet {@code {contenu, total, page}} — aurait change la forme de
 * la reponse selon la presence d'un parametre, donnant un contrat impossible a
 * decrire proprement en OpenAPI et cassant tous les clients existants. Le total
 * voyage donc dans {@code X-Total-Count}, convention REST repandue, et la forme du
 * corps ne change jamais.
 *
 * <p>Sans parametre {@code page} ni {@code taille}, le comportement est <b>identique
 * a l'existant</b> : la liste complete, avec son total en en-tete.
 */
@Component
public class Pagination {

    /** Nombre maximal d'elements par page, quelle que soit la demande du client. */
    public static final int TAILLE_MAX = 200;

    public static final String ENTETE_TOTAL = "X-Total-Count";
    public static final String ENTETE_PAGE = "X-Page";
    public static final String ENTETE_TAILLE = "X-Taille";

    private final ConfigurationMetier configuration;

    public Pagination(ConfigurationMetier configuration) {
        this.configuration = configuration;
    }

    /**
     * Rend la liste, paginee si le client l'a demande.
     *
     * @param page   numero de page demande (0 = premiere), ou {@code null}
     * @param taille taille de page demandee, ou {@code null} pour celle de
     *               {@code ConfigZumm.ini}
     * @param tout   acces a la liste complete, utilise quand rien n'est demande
     * @param parPage acces pagine
     */
    public <T> ResponseEntity<List<T>> reponse(
            Integer page,
            Integer taille,
            Supplier<List<T>> tout,
            Function<Pageable, Page<T>> parPage) {
        return reponse(page, taille, null, tout, parPage);
    }

    /** Idem, avec un tri serveur optionnel (nom de propriete, {@code null} = ordre naturel). */
    public <T> ResponseEntity<List<T>> reponse(
            Integer page,
            Integer taille,
            String tri,
            Supplier<List<T>> tout,
            Function<Pageable, Page<T>> parPage) {

        if (page == null && taille == null && tri == null) {
            List<T> liste = tout.get();
            return ResponseEntity.ok()
                    .header(ENTETE_TOTAL, String.valueOf(liste.size()))
                    .body(liste);
        }

        int numero = page == null ? 0 : page;
        if (numero < 0) {
            throw new RequeteInvalide("Le numero de page ne peut pas etre negatif : " + numero);
        }
        int demandee = taille == null ? configuration.seuils().taillePageParDefaut() : taille;
        if (demandee < 1) {
            throw new RequeteInvalide("La taille de page doit valoir au moins 1 : " + demandee);
        }
        // Plafond : une taille de page arbitraire annulerait tout le benefice.
        int effective = Math.min(demandee, TAILLE_MAX);

        Pageable pagination = tri == null || tri.isBlank()
                ? PageRequest.of(numero, effective)
                : PageRequest.of(numero, effective, Sort.by(tri));
        Page<T> resultat = parPage.apply(pagination);

        return ResponseEntity.ok()
                .header(ENTETE_TOTAL, String.valueOf(resultat.getTotalElements()))
                .header(ENTETE_PAGE, String.valueOf(numero))
                .header(ENTETE_TAILLE, String.valueOf(effective))
                .body(resultat.getContent());
    }
}
