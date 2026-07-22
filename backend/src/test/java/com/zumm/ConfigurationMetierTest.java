package com.zumm;

import static org.assertj.core.api.Assertions.assertThat;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import java.io.StringReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifie la lecture de {@code ConfigZumm.ini} (US-025) : sections, commentaires,
 * types, listes, et repli sur les defauts quand une cle manque ou est invalide.
 */
class ConfigurationMetierTest {

    @Test
    @DisplayName("lit les sections, seuils et listes du gabarit")
    void litLeGabarit() throws Exception {
        String ini = """
                [application]
                langue_par_defaut = fr
                langues_actives = fr,en,ar

                [seuils]
                poids_ruche_alerte_kg = 15   ; commentaire de fin de ligne
                temperature_min_celsius = 32
                temperature_max_celsius = 36
                humidite_max_pourcent = 70

                [visites]
                delai_alerte_jours = 21

                [carte]
                arrondi_degres_public = 2
                """;

        SeuilsMetier seuils = ConfigurationMetier.extraireDe(new StringReader(ini));

        assertThat(seuils.langueParDefaut()).isEqualTo("fr");
        assertThat(seuils.languesActives()).containsExactly("fr", "en", "ar");
        assertThat(seuils.poidsRucheAlerteKg()).isEqualTo(15);
        assertThat(seuils.temperatureMinCelsius()).isEqualTo(32);
        assertThat(seuils.temperatureMaxCelsius()).isEqualTo(36);
        assertThat(seuils.humiditeMaxPourcent()).isEqualTo(70);
        assertThat(seuils.delaiAlerteJours()).isEqualTo(21);
        assertThat(seuils.arrondiDegresPublic()).isEqualTo(2);
    }

    @Test
    @DisplayName("retombe sur les defauts pour une cle absente ou non numerique")
    void repliSurLesDefauts() throws Exception {
        String ini = """
                [seuils]
                poids_ruche_alerte_kg = pas_un_nombre
                # humidite_max_pourcent est absente
                """;

        SeuilsMetier seuils = ConfigurationMetier.extraireDe(new StringReader(ini));
        SeuilsMetier defauts = SeuilsMetier.defauts();

        assertThat(seuils.poidsRucheAlerteKg())
                .as("valeur non numerique -> defaut")
                .isEqualTo(defauts.poidsRucheAlerteKg());
        assertThat(seuils.humiditeMaxPourcent())
                .as("cle absente -> defaut")
                .isEqualTo(defauts.humiditeMaxPourcent());
    }

    @Test
    @DisplayName("ignore les commentaires ; et # et les lignes vides")
    void ignoreCommentairesEtLignesVides() throws Exception {
        String ini = """
                ; fichier de configuration metier
                # encore un commentaire

                [visites]
                delai_alerte_jours = 30
                """;

        SeuilsMetier seuils = ConfigurationMetier.extraireDe(new StringReader(ini));

        assertThat(seuils.delaiAlerteJours()).isEqualTo(30);
    }
}
