package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifie que la parite linguistique est controlee automatiquement et non a l'oeil,
 * dans l'esprit de {@code scripts/check-sync.sh} qui joue le meme role pour le
 * cahier des charges.
 *
 * <p>Le francais est la langue source : toute cle qui y figure doit exister en
 * anglais et en arabe, et aucune traduction ne doit introduire de cle inconnue.
 */
class PariteI18nTest {

    private static final String LANGUE_SOURCE = "fr";

    @ParameterizedTest(name = "les cles de {0} correspondent exactement a celles du francais")
    @ValueSource(strings = {"en", "ar"})
    @DisplayName("chaque traduction couvre exactement les cles de la langue source")
    void chaqueTraductionCouvreLesClesSource(String langue) throws IOException {
        Set<String> clesSource = chargerCles(LANGUE_SOURCE);
        Set<String> clesTraduites = chargerCles(langue);

        assertThat(clesSource)
                .as("la langue source ne doit pas etre vide")
                .isNotEmpty();
        assertThat(clesTraduites)
                .as("cles manquantes ou en trop dans messages_%s.properties", langue)
                .containsExactlyInAnyOrderElementsOf(clesSource);
    }

    @ParameterizedTest(name = "aucune valeur vide en {0}")
    @ValueSource(strings = {"fr", "en", "ar"})
    @DisplayName("aucune traduction n'est laissee vide")
    void aucuneTraductionVide(String langue) throws IOException {
        Properties proprietes = charger(langue);
        for (String cle : proprietes.stringPropertyNames()) {
            assertThat(proprietes.getProperty(cle).trim())
                    .as("la cle %s est vide en %s", cle, langue)
                    .isNotEmpty();
        }
    }

    private Set<String> chargerCles(String langue) throws IOException {
        return charger(langue).stringPropertyNames();
    }

    private Properties charger(String langue) throws IOException {
        String chemin = "messages_" + langue + ".properties";
        Properties proprietes = new Properties();
        try (InputStream flux = getClass().getClassLoader().getResourceAsStream(chemin)) {
            assertThat(flux).as("fichier %s introuvable", chemin).isNotNull();
            proprietes.load(new InputStreamReader(flux, StandardCharsets.UTF_8));
        }
        return proprietes;
    }
}
