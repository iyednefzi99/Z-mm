package com.zumm.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduit les exceptions applicatives en reponses HTTP normalisees
 * ({@link ProblemDetail}, RFC 7807), avec un statut juste et un message
 * exploitable — jamais de trace technique renvoyee au client.
 */
@RestControllerAdvice
public class GestionnaireExceptions {

    @ExceptionHandler(RessourceIntrouvable.class)
    ProblemDetail introuvable(RessourceIntrouvable e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RequeteInvalide.class)
    ProblemDetail invalide(RequeteInvalide e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Regle metier violee (SPRINT-22) : 409, et non 400.
     *
     * <p>La requete est valide ; c'est l'etat du systeme qui s'y oppose — une
     * ruche sous carence, par exemple. L'appelant n'a rien a corriger dans son
     * corps de requete, il a une decision a prendre. Le message porte donc ce
     * qu'il faut pour la prendre : quoi, jusqu'a quand, et comment passer outre.
     */
    @ExceptionHandler(RegleMetierViolee.class)
    ProblemDetail regleMetier(RegleMetierViolee e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Conflit de version (SPRINT-24) : la ressource a bouge depuis la lecture.
     *
     * <p>Le statut est le meme que pour une regle metier — 409 — mais la reponse
     * porte en plus {@code versionServeur}. C'est ce qui distingue un refus
     * exploitable d'un simple echec : l'interface peut proposer un choix (garder
     * ma saisie, garder celle du serveur) parce qu'elle sait ce qui a change.
     *
     * <p>Le serveur ne fusionne rien. Decider laquelle de deux observations dit
     * vrai sur le couvain d'une colonie est un arbitrage d'apiculteur, pas une
     * regle de precedence.
     */
    @ExceptionHandler(ConflitVersion.class)
    ProblemDetail conflitVersion(ConflitVersion e) {
        ProblemDetail probleme =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        probleme.setProperty("versionServeur", String.valueOf(e.versionServeur()));
        return probleme;
    }

    /**
     * Violation d'integrite referentielle : typiquement la suppression d'un fermier
     * encore rattache a des fermes (FK {@code ON DELETE RESTRICT}). On repond 409
     * plutot que 500 — c'est un conflit d'etat, pas une erreur serveur.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrite(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Operation impossible : la ressource est referencee par d'autres donnees.");
    }

    /**
     * Corps illisible : JSON malforme ou valeur d'enumeration inconnue (par
     * exemple un role d'agent hors liste). 400, sans exposer la cause technique.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail illisible(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Le corps de la requete est illisible ou contient une valeur non reconnue.");
    }

    /** Erreurs de validation Bean Validation sur le corps de requete : 400 detaille. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Le corps de la requete est invalide.");
        Map<String, String> champs = new LinkedHashMap<>();
        for (var erreur : e.getBindingResult().getFieldErrors()) {
            champs.putIfAbsent(erreur.getField(), erreur.getDefaultMessage());
        }
        probleme.setProperty("champs", champs);
        return probleme;
    }
}
