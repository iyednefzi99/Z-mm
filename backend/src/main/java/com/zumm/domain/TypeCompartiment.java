package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Element de composition d'une ruche (US-004) : le corps ou une hausse. */
public enum TypeCompartiment {
    CORPS("corps"),
    HAUSSE("hausse");

    private final String enBase;

    TypeCompartiment(String enBase) {
        this.enBase = enBase;
    }

    @JsonValue
    public String enBase() {
        return enBase;
    }

    @JsonCreator
    public static TypeCompartiment depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(t -> t.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Type de compartiment inconnu : " + valeur));
    }
}
