package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Evaluation qualitative de l'essaim (US-009). Libelle en base et JSON via {@link #enBase()}. */
public enum EffectifQualitatif {
    FAIBLE("faible"),
    MOYEN("moyen"),
    FORT("fort"),
;
    private final String enBase;

    EffectifQualitatif(String enBase) {
        this.enBase = enBase;
    }

    @JsonValue
    public String enBase() {
        return enBase;
    }

    @JsonCreator
    public static EffectifQualitatif depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(v -> v.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Valeur inconnue pour EffectifQualitatif : " + valeur));
    }
}
