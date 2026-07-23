package com.zumm.web;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
     * Violation d'integrite referentielle : typiquement la suppression d'un fermier
     * encore rattache a des fermes (FK {@code ON DELETE RESTRICT}). On repond 409
     * plutot que 500 — c'est un conflit d'etat, pas une erreur serveur.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrite(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Operation impossible : la ressource est referencee par d'autres donnees.");
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
