package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mappe {@link EtatSante} sur son libelle en base. */
@Converter(autoApply = true)
public class EtatSanteConverter implements AttributeConverter<EtatSante, String> {

    @Override
    public String convertToDatabaseColumn(EtatSante valeur) {
        return valeur == null ? null : valeur.enBase();
    }

    @Override
    public EtatSante convertToEntityAttribute(String valeur) {
        return valeur == null ? null : EtatSante.depuisEnBase(valeur);
    }
}
