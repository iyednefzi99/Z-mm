package com.zumm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;

/**
 * Convertit {@link RoleAgent} vers son libelle en base ({@code apiculteur}, ...)
 * plutot que vers le nom de la constante Java. Aligne le mapping JPA sur la
 * contrainte {@code CHECK} de la table {@code agent} et sur les roles Keycloak.
 */
@Converter(autoApply = true)
public class RoleAgentConverter implements AttributeConverter<RoleAgent, String> {

    @Override
    public String convertToDatabaseColumn(RoleAgent role) {
        return role == null ? null : role.enBase();
    }

    @Override
    public RoleAgent convertToEntityAttribute(String valeur) {
        if (valeur == null) {
            return null;
        }
        return Arrays.stream(RoleAgent.values())
                .filter(role -> role.enBase().equals(valeur))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Role inconnu en base : " + valeur));
    }
}
