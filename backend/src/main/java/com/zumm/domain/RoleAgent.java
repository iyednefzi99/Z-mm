package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Roles metier d'un agent (US-005), enumeration du dictionnaire de donnees.
 *
 * <p>Ces valeurs sont aussi les roles de royaume Keycloak
 * ({@code infra/keycloak/realm-zumm.json}) et la contrainte {@code CHECK} de la
 * table {@code agent} : les trois sources doivent rester alignees. Le nom en base
 * est le libelle en minuscules ({@link #enBase()}), pas le nom de la constante ;
 * l'API JSON expose et accepte ce meme libelle.
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

    /** Libelle stocke en base, porte par le jeton Keycloak et expose en JSON. */
    @JsonValue
    public String enBase() {
        return enBase;
    }

    /** Resout un role depuis son libelle, pour la deserialisation JSON et la base. */
    @JsonCreator
    public static RoleAgent depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(role -> role.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Role inconnu : " + valeur));
    }
}
