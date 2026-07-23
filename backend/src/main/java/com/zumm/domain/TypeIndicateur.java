package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Indicateur mesure d'une ruche (US-016) : poids, temperature, humidite, activite. */
public enum TypeIndicateur {
    POIDS("poids"),
    TEMPERATURE("temperature"),
    HUMIDITE("humidite"),
    ACTIVITE("activite");

    private final String enBase;

    TypeIndicateur(String enBase) {
        this.enBase = enBase;
    }

    @JsonValue
    public String enBase() {
        return enBase;
    }

    @JsonCreator
    public static TypeIndicateur depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(t -> t.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Indicateur inconnu : " + valeur));
    }
}
