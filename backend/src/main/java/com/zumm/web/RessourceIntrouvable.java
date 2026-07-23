package com.zumm.web;

/**
 * Ressource demandee absente <em>pour le tenant courant</em> : donne lieu a un
 * 404. La RLS et le discriminant {@code @TenantId} garantissent qu'une ressource
 * appartenant a un autre tenant est indistinguable d'une ressource inexistante.
 */
public class RessourceIntrouvable extends RuntimeException {

    public RessourceIntrouvable(String message) {
        super(message);
    }

    public static RessourceIntrouvable de(String entite, Long id) {
        return new RessourceIntrouvable(entite + " introuvable : " + id);
    }
}
