package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mappe {@link TypeIndicateur} sur son libelle en base, aligne sur la contrainte CHECK. */
@Converter(autoApply = true)
public class TypeIndicateurConverter implements AttributeConverter<TypeIndicateur, String> {

    @Override
    public String convertToDatabaseColumn(TypeIndicateur valeur) {
        return valeur == null ? null : valeur.enBase();
    }

    @Override
    public TypeIndicateur convertToEntityAttribute(String valeur) {
        return valeur == null ? null : TypeIndicateur.depuisEnBase(valeur);
    }
}
