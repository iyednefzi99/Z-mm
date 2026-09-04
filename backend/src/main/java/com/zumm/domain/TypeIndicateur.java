package com.zumm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Indicateur mesure d'une ruche (US-016) : poids, temperature, humidite, activite. */
public enum TypeIndicateur {
    POIDS("poids"),
    TEMPERATURE("temperature"),
    HUMIDITE("humidite"),
    ACTIVITE("activite"),

    /**
     * Niveau de batterie du capteur, en pourcent (SPRINT-26, lot F1).
     *
     * <p>Ne dit rien de la colonie : c'est l'etat du MATERIEL qui l'observe.
     * BeeLog Digital et Onibi se font tous deux reprocher les pannes de batterie
     * silencieuses — la balance cesse d'emettre, et personne ne s'en apercoit
     * avant la visite suivante. Le reproche porte sur l'absence d'alerte, pas
     * sur l'absence de mesure : d'ou une valeur d'enumeration de plus, et
     * {@code SeuilAlerteService} qui s'en occupe comme des autres.
     */
    ALIMENTATION("alimentation");

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
