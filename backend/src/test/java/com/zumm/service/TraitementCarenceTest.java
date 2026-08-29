package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zumm.domain.Traitement;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Delai de carence : a partir de quand le miel d'une ruche traitee redevient
 * recoltable (SPRINT-20).
 *
 * <p>C'est un point <strong>reglementaire</strong>, pas un confort : recolter
 * sous carence met sur le marche un miel qui porte des residus. La regle vit sur
 * l'entite et prend le jour en parametre — c'est ce qui la rend eprouvable ici,
 * sans base ni horloge injectee.
 */
class TraitementCarenceTest {

    private static final LocalDate DEBUT = LocalDate.of(2026, 7, 1);

    private static Traitement traitement(LocalDate fin, Integer carence) {
        // Ruche et agent nuls : la regle de carence ne depend que des dates.
        Traitement t = new Traitement(null, null, "Apivar", "varroa", DEBUT);
        t.setDateFin(fin);
        t.setDelaiCarenceJours(carence);
        return t;
    }

    @Test
    @DisplayName("sans delai de carence declare, rien ne bloque la recolte")
    void sansCarence() {
        assertThat(traitement(DEBUT.plusDays(10), null).sousCarence(DEBUT.plusDays(20))).isFalse();
    }

    @Test
    @DisplayName("un traitement qui COURT ENCORE bloque la recolte")
    void traitementEnCours() {
        // Le cas qu'on oublie : `date_fin` nulle, donc `date_retrait` nulle. Si
        // l'on ne repondait « sous carence » que sur une date de retrait connue,
        // on autoriserait a recolter PENDANT le traitement lui-meme.
        Traitement enCours = traitement(null, 14);

        assertThat(enCours.getDateRetrait()).isNull();
        assertThat(enCours.sousCarence(DEBUT.plusDays(3))).isTrue();
        assertThat(enCours.sousCarence(DEBUT.plusDays(90))).isTrue();
    }

    @Test
    @DisplayName("avant le debut du traitement, la carence n'a pas commence")
    void avantLeDebut() {
        assertThat(traitement(DEBUT.plusDays(10), 14).sousCarence(DEBUT.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("la carence court jusqu'au dernier jour inclus, puis se leve")
    void bornesDeLaCarence() {
        // Traitement du 1er au 11 juillet, carence de 14 jours : retrait au 25.
        // Le 25 est encore bloque — un delai « de 14 jours » qui se leverait le
        // quatorzieme n'en durerait que treize.
        Traitement t = traitement(DEBUT.plusDays(10), 14);

        assertThat(t.sousCarence(LocalDate.of(2026, 7, 11))).isTrue();
        assertThat(t.sousCarence(LocalDate.of(2026, 7, 25))).isTrue();
        assertThat(t.sousCarence(LocalDate.of(2026, 7, 26))).isFalse();
    }

    @Test
    @DisplayName("une carence nulle leve le blocage des la fin du traitement")
    void carenceNulle() {
        Traitement t = traitement(DEBUT.plusDays(10), 0);

        assertThat(t.sousCarence(LocalDate.of(2026, 7, 11))).isTrue();
        assertThat(t.sousCarence(LocalDate.of(2026, 7, 12))).isFalse();
    }
}
