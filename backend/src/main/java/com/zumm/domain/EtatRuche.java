package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Cycle de vie d'une ruche (US-004), machine a etats du dictionnaire :
 * creee -> peuplee -> active -> en division -> en collecte -> cloturee.
 * Le libelle en base et en JSON est le code ASCII ({@link #enBase()}).
 */
public enum EtatRuche {
    CREEE("creee"),
    PEUPLEE("peuplee"),
    ACTIVE("active"),
    EN_DIVISION("en_division"),
    EN_COLLECTE("en_collecte"),
    CLOTUREE("cloturee");

    private final String enBase;

    EtatRuche(String enBase) {
        this.enBase = enBase;
    }

    @JsonValue
    public String enBase() {
        return enBase;
    }

    @JsonCreator
    public static EtatRuche depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(e -> e.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("État de ruche inconnu : " + valeur));
    }
}
