package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Motif d'une visite (US-009). Libelle en base et JSON via {@link #enBase()}. */
public enum RaisonVisite {
    CONTROLE("controle"),
    RECOLTE("recolte"),
    TRAITEMENT("traitement"),
    NOURRISSAGE("nourrissage"),
    DIVISION("division"),
    AUTRE("autre"),
;
    private final String enBase;

    RaisonVisite(String enBase) {
        this.enBase = enBase;
    }

    @JsonValue
    public String enBase() {
        return enBase;
    }

    @JsonCreator
    public static RaisonVisite depuisEnBase(String valeur) {
        return Arrays.stream(values())
                .filter(v -> v.enBase.equalsIgnoreCase(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Valeur inconnue pour RaisonVisite : " + valeur));
    }
}
