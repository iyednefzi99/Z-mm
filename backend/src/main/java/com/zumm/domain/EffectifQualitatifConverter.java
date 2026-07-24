package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mappe {@link EffectifQualitatif} sur son libelle en base. */
@Converter(autoApply = true)
public class EffectifQualitatifConverter implements AttributeConverter<EffectifQualitatif, String> {

    @Override
    public String convertToDatabaseColumn(EffectifQualitatif valeur) {
        return valeur == null ? null : valeur.enBase();
    }

    @Override
    public EffectifQualitatif convertToEntityAttribute(String valeur) {
        return valeur == null ? null : EffectifQualitatif.depuisEnBase(valeur);
    }
}
