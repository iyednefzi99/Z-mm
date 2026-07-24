package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mappe {@link RaisonVisite} sur son libelle en base. */
@Converter(autoApply = true)
public class RaisonVisiteConverter implements AttributeConverter<RaisonVisite, String> {

    @Override
    public String convertToDatabaseColumn(RaisonVisite valeur) {
        return valeur == null ? null : valeur.enBase();
    }

    @Override
    public RaisonVisite convertToEntityAttribute(String valeur) {
        return valeur == null ? null : RaisonVisite.depuisEnBase(valeur);
    }
}
