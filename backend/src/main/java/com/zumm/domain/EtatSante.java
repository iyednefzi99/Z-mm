package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Etat sanitaire de la colonie (US-009). Libelle en base et JSON via {@link #enBase()}. */
public enum EtatSante {
    BON("bon"),
    MOYEN("moyen"),
    MAUVAIS("mauvais"),
;
    private final String enBase;

    EtatSante(String enBase) {
        this.enBase = enBase;
    }

    @JsonValue
    public String enBase() {
        return enBase;
    }

    @JsonCreator
    public static EtatSante depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(v -> v.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Valeur inconnue pour EtatSante : " + valeur));
    }
}
