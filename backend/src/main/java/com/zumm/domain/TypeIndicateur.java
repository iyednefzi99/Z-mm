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
    ALIMENTATION("alimentation"),

    /**
     * Inclinaison de la ruche, en degres (SPRINT-31, lot F2).
     *
     * <p>Ne dit rien de la colonie non plus : c'est la POSITION de la caisse.
     * Une ruche renversee par le vent, un sanglier ou un voleur sort de la
     * verticale, et c'est le signal le plus direct qu'un capteur puisse donner.
     *
     * <p>Peu de materiel en pousse aujourd'hui, et ce n'est pas la question :
     * l'API d'ingestion est generique, et le point est de ne pas obliger celui
     * qui en a a detourner un autre indicateur. Meme raisonnement qu'a
     * l'alimentation au SPRINT-26 — une valeur d'enumeration, un seuil, et
     * {@code SeuilAlerteService} s'en occupe comme des autres.
     */
    INCLINAISON("inclinaison");

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
