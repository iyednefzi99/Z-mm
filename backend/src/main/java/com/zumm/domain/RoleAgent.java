package com.zumm.domain;

/**
 * Roles metier d'un agent (US-005), enumeration du dictionnaire de donnees.
 *
 * <p>Ces valeurs sont aussi les roles de royaume Keycloak
 * ({@code infra/keycloak/realm-zumm.json}) et la contrainte {@code CHECK} de la
 * table {@code agent} : les trois sources doivent rester alignees. Le nom en base
 * est le libelle en minuscules ({@link #enBase()}), pas le nom de la constante.
 */
public enum RoleAgent {
    APICULTEUR("apiculteur"),
    SUPERVISEUR("superviseur"),
    RESPONSABLE("responsable"),
    ADMIN("admin");

    private final String enBase;

    RoleAgent(String enBase) {
        this.enBase = enBase;
    }

    /** Libelle stocke en base et porte par le jeton Keycloak. */
    public String enBase() {
        return enBase;
    }
}
