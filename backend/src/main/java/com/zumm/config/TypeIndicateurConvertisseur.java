package com.zumm.config;

import com.zumm.domain.TypeIndicateur;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Convertit un parametre de requete ({@code ?type=poids}) en {@link TypeIndicateur}.
 *
 * <p>Le {@code @JsonCreator} de l'enum ne couvre que les corps JSON ; la liaison des
 * parametres de requete passe, elle, par les {@code Converter} Spring. On y reutilise
 * le meme libelle « en base » (poids, temperature…), et non le nom de la constante.
 */
@Component
public class TypeIndicateurConvertisseur implements Converter<String, TypeIndicateur> {

    @Override
    public TypeIndicateur convert(String source) {
        return TypeIndicateur.depuisEnBase(source);
    }
}
