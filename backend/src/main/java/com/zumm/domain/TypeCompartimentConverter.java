package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mappe {@link TypeCompartiment} sur son libelle en base, aligne sur la contrainte CHECK. */
@Converter(autoApply = true)
public class TypeCompartimentConverter implements AttributeConverter<TypeCompartiment, String> {

    @Override
    public String convertToDatabaseColumn(TypeCompartiment valeur) {
        return valeur == null ? null : valeur.enBase();
    }

    @Override
    public TypeCompartiment convertToEntityAttribute(String valeur) {
        return valeur == null ? null : TypeCompartiment.depuisEnBase(valeur);
    }
}
