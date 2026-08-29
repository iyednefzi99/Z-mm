package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zumm.domain.ComptageVarroa;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Calcul du taux d'infestation par varroa et lecture contre les seuils
 * (SPRINT-20).
 *
 * <p>Ce sont des fonctions pures : elles se testent sans base ni conteneur, et
 * c'est precisement pourquoi le taux a ete laisse au service plutot que confie a
 * une colonne generee.
 */
class ComptageVarroaServiceTest {

    private static final LocalDate JOUR = LocalDate.of(2026, 8, 26);

    // Ruche et agent sont laisses nuls : le calcul du taux n'en depend pas, et
    // les monter demanderait un site, une ferme et un agent complets pour ne rien
    // eprouver de plus. Le rattachement, lui, est couvert par le test
    // d'integration.

    private static ComptageVarroa echantillon(int varroas, Integer abeilles) {
        ComptageVarroa c = new ComptageVarroa(null, null, JOUR, "sucre_glace", varroas);
        c.setAbeillesEchantillon(abeilles);
        return c;
    }

    private static ComptageVarroa lange(int varroas, Integer jours) {
        ComptageVarroa c = new ComptageVarroa(null, null, JOUR, "lange", varroas);
        c.setJoursExposition(jours);
        return c;
    }

    @Test
    @DisplayName("un echantillon rend un pourcentage : 9 varroas sur 300 abeilles font 3 %")
    void tauxParEchantillon() {
        assertThat(ComptageVarroaService.taux(echantillon(9, 300)))
                .isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    @DisplayName("un lange rend une chute par jour : 12 varroas en 3 jours font 4 par jour")
    void tauxParLange() {
        assertThat(ComptageVarroaService.taux(lange(12, 3)))
                .isEqualByComparingTo(new BigDecimal("4.00"));
    }

    @Test
    @DisplayName("le meme nombre de varroas donne deux verdicts opposes selon la methode")
    void memeNombreDeuxVerdicts() {
        // C'est tout l'argument contre une colonne « taux » unique : trois
        // varroas, c'est une chute d'un par jour — anodine — ou 3 % d'un
        // echantillon de cent abeilles, qui declenche un traitement. Stocker un
        // seul nombre sans son unite reviendrait a confondre les deux.
        assertThat(ComptageVarroaService.verdict(lange(3, 3))).isEqualTo("faible");
        assertThat(ComptageVarroaService.verdict(echantillon(3, 100))).isEqualTo("traiter");
    }

    @ParameterizedTest(name = "{0} varroas sur {1} abeilles -> {2}")
    @CsvSource({
        "0,   300, faible",
        "2,   300, faible",
        "3,   300, surveiller",
        "8,   300, surveiller",
        "9,   300, traiter",
        "30,  300, traiter",
    })
    @DisplayName("les seuils par echantillon : 1 % surveille, 3 % declenche")
    void seuilsEchantillon(int varroas, int abeilles, String attendu) {
        assertThat(ComptageVarroaService.verdict(echantillon(varroas, abeilles)))
                .isEqualTo(attendu);
    }

    @ParameterizedTest(name = "{0} varroas en {1} jours -> {2}")
    @CsvSource({
        "3,  3, faible",
        "6,  3, surveiller",
        "15, 3, traiter",
    })
    @DisplayName("les seuils par lange : 2 par jour surveille, 5 declenche")
    void seuilsLange(int varroas, int jours, String attendu) {
        assertThat(ComptageVarroaService.verdict(lange(varroas, jours))).isEqualTo(attendu);
    }

    @Test
    @DisplayName("sans denominateur, le verdict est « inconnu » et jamais « faible »")
    void denominateurManquant() {
        // Rassurer faute de pouvoir calculer serait la pire des reponses.
        assertThat(ComptageVarroaService.taux(echantillon(9, null))).isNull();
        assertThat(ComptageVarroaService.verdict(echantillon(9, null))).isEqualTo("inconnu");
        assertThat(ComptageVarroaService.verdict(lange(9, null))).isEqualTo("inconnu");
    }

    @Test
    @DisplayName("le denominateur attendu depend de la methode, et l'autre est refuse")
    void validiteDuDenominateur() {
        assertThat(echantillon(9, 300).estValide()).isTrue();
        assertThat(lange(12, 3).estValide()).isTrue();

        // Un lange renseigne comme un echantillon : incalculable, donc invalide.
        ComptageVarroa melange = lange(12, 3);
        melange.setAbeillesEchantillon(300);
        assertThat(melange.estValide()).isFalse();
    }
}
