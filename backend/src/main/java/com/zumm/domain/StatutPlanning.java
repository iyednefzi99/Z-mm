package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Statut d'approbation d'un planning (US-008). Libelle en base et JSON via {@link #enBase()}. */
public enum StatutPlanning {
    PROPOSE("propose"),
    APPROUVE("approuve"),
    REFUSE("refuse"),
;
    private final String enBase;

    StatutPlanning(String enBase) {
        this.enBase = enBase;
    }

    @JsonValue
    public String enBase() {
        return enBase;
    }

    @JsonCreator
    public static StatutPlanning depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(v -> v.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Valeur inconnue pour StatutPlanning : " + valeur));
    }
}
