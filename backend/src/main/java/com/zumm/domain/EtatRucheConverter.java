package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mappe {@link EtatRuche} sur son libelle en base, aligne sur la contrainte CHECK. */
@Converter(autoApply = true)
public class EtatRucheConverter implements AttributeConverter<EtatRuche, String> {

    @Override
    public String convertToDatabaseColumn(EtatRuche valeur) {
        return valeur == null ? null : valeur.enBase();
    }

    @Override
    public EtatRuche convertToEntityAttribute(String valeur) {
        return valeur == null ? null : EtatRuche.depuisEnBase(valeur);
    }
}
