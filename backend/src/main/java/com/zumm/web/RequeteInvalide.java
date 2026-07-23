package com.zumm.web;

/**
 * Requete refusee pour une raison metier (et non de simple format) : par exemple
 * une ferme rattachee a un fermier qui n'existe pas dans le tenant. Donne lieu a
 * un 400.
 */
public class RequeteInvalide extends RuntimeException {

    public RequeteInvalide(String message) {
        super(message);
    }
}
