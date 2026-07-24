package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mappe {@link StatutPlanning} sur son libelle en base. */
@Converter(autoApply = true)
public class StatutPlanningConverter implements AttributeConverter<StatutPlanning, String> {

    @Override
    public String convertToDatabaseColumn(StatutPlanning valeur) {
        return valeur == null ? null : valeur.enBase();
    }

    @Override
    public StatutPlanning convertToEntityAttribute(String valeur) {
        return valeur == null ? null : StatutPlanning.depuisEnBase(valeur);
    }
}
