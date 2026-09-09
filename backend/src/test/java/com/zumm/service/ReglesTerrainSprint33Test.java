package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.repository.CouvertSolRepository;
import com.zumm.repository.CouvertSolRepository.RucherAVerifier;
import com.zumm.repository.ZoneTraiteeRepository;
import com.zumm.repository.ZoneTraiteeRepository.ExpositionBrute;
import com.zumm.service.regles.RegleVerificationCouvert;
import com.zumm.service.regles.RegleZoneTraiteeProche;
import com.zumm.service.regles.TacheProposee;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Les deux règles du SPRINT-33 (lot K), et ce qui décide de leur utilité.
 *
 * <p>Une règle qui engendre des tâches se juge sur <strong>trois</strong> points,
 * et pas un de plus : la <em>maille</em> (une tâche pour quoi ?), la
 * <em>clé</em> (quand la repropose-t-elle ?) et la <em>priorité</em> (qu'est-ce
 * qu'elle déplace dans la liste ?). Ce sont les trois qui sont testés ici,
 * parce que ce sont les trois qui font qu'une liste reste lue.
 *
 * <p>Le précédent est connu dans ce dépôt : {@code RegleStockBas} a une clé
 * mensuelle, ni fixe — elle ne reproposerait jamais rien — ni journalière — la
 * liste serait illisible en une semaine.
 */
@ExtendWith(MockitoExtension.class)
class ReglesTerrainSprint33Test {

    @Mock private CouvertSolRepository couverts;
    @Mock private ZoneTraiteeRepository zones;
    @Mock private ConfigurationMetier configuration;

    private RegleVerificationCouvert verification;
    private RegleZoneTraiteeProche traitements;

    @BeforeEach
    void monter() {
        lenient().when(configuration.seuils()).thenReturn(SeuilsMetier.defauts());
        verification = new RegleVerificationCouvert(couverts, configuration);
        traitements = new RegleZoneTraiteeProche(zones, configuration);
    }

    // ─── Vérification terrain (ground truthing) ─────────────────────────────

    @Test
    @DisplayName("une tâche par RUCHER, jamais une par parcelle")
    void uneTacheParRucher() {
        // Quarante parcelles autour d'un rucher de plaine : quarante tâches
        // « vérifier la parcelle 17 843 » rendraient la liste illisible en une
        // matinée, et une liste qu'on n'ouvre plus ne rappelle rien.
        when(couverts.ruchersAVerifier(anyInt())).thenReturn(List.of(
                new RucherAVerifier(1L, "Colline", 40),
                new RucherAVerifier(2L, "Vallon", 3)));

        List<TacheProposee> proposees = verification.proposer(LocalDate.of(2026, 4, 12));

        assertThat(proposees).hasSize(2);
        assertThat(proposees.get(0).libelle()).contains("40 parcelle(s)", "Colline");
        // La tâche ne porte aucune ruche : rien d'autre ne la situerait à l'écran,
        // d'où le nom du rucher DANS le libellé.
        assertThat(proposees.get(0).rucheId()).isNull();
    }

    @Test
    @DisplayName("une proposition par SAISON : la clé porte l'année, pas le mois")
    void clePorteLAnnee() {
        when(couverts.ruchersAVerifier(anyInt()))
                .thenReturn(List.of(new RucherAVerifier(1L, "Colline", 2)));

        String avril = verification.proposer(LocalDate.of(2026, 4, 12)).get(0).cle();
        String juin = verification.proposer(LocalDate.of(2026, 6, 30)).get(0).cle();
        String anneeSuivante = verification.proposer(LocalDate.of(2027, 4, 12)).get(0).cle();

        // Le ground truthing est un geste de printemps : on regarde ce qui a levé.
        // Deux passages la même année ne doivent pas produire deux tournées…
        assertThat(avril).isEqualTo(juin).isEqualTo("verification-couvert:1:2026");
        // …et l'année suivante doit en reproposer une, sans quoi la couche ne
        // serait jamais revérifiée.
        assertThat(anneeSuivante).isNotEqualTo(avril);
    }

    @Test
    @DisplayName("priorité normale : une couche imprécise ne met aucune colonie en danger")
    void prioriteNormale() {
        when(couverts.ruchersAVerifier(anyInt()))
                .thenReturn(List.of(new RucherAVerifier(1L, "Colline", 2)));

        // Ce qui est urgent chasse ce qui est important. Monter cette tâche en
        // « haute » la ferait passer devant un retrait de traitement sous carence.
        assertThat(verification.proposer(LocalDate.of(2026, 4, 12)).get(0).priorite())
                .isEqualTo("normale");
    }

    @Test
    @DisplayName("aucune parcelle en attente : aucune tâche")
    void rienAVerifier() {
        when(couverts.ruchersAVerifier(anyInt())).thenReturn(List.of());

        assertThat(verification.proposer(LocalDate.now())).isEmpty();
    }

    // ─── Zone traitée à proximité ───────────────────────────────────────────

    @Test
    @DisplayName("la fenêtre de vigilance est de quatorze jours")
    void fenetreQuatorzeJours() {
        LocalDate jour = LocalDate.of(2026, 5, 20);
        when(zones.ruchersExposes(anyInt(), any())).thenReturn(List.of());

        traitements.proposer(jour);

        // Un traitement déclaré il y a six mois n'appelle aucun geste ; déclaré
        // avant-hier, il appelle un coup d'œil aux planches d'envol.
        org.mockito.Mockito.verify(zones)
                .ruchersExposes(anyInt(), org.mockito.ArgumentMatchers.eq(jour.minusDays(14)));
    }

    @Test
    @DisplayName("la clé porte la DATE de la dernière déclaration : une nouvelle rouvre")
    void cleRouvreSurNouvelleDeclaration() {
        LocalDate jour = LocalDate.of(2026, 5, 20);
        when(zones.ruchersExposes(anyInt(), any()))
                .thenReturn(List.of(new ExpositionBrute(1L, "Colline", 2,
                        LocalDate.of(2026, 5, 18), new BigDecimal("340"))))
                .thenReturn(List.of(new ExpositionBrute(1L, "Colline", 3,
                        LocalDate.of(2026, 5, 19), new BigDecimal("120"))));

        String premiere = traitements.proposer(jour).get(0).cle();
        String seconde = traitements.proposer(jour).get(0).cle();

        // Une clé figée sur le rucher resterait muette à la déclaration suivante,
        // qui est pourtant l'information neuve.
        assertThat(premiere).isEqualTo("zone-traitee-proche:1:2026-05-18");
        assertThat(seconde).isEqualTo("zone-traitee-proche:1:2026-05-19");
    }

    @Test
    @DisplayName("la tâche SIGNALE la distance, elle ne conclut pas à l'exposition")
    void signaleSansConclure() {
        when(zones.ruchersExposes(anyInt(), any()))
                .thenReturn(List.of(new ExpositionBrute(1L, "Colline", 1,
                        LocalDate.of(2026, 5, 18), new BigDecimal("340"))));

        TacheProposee tache = traitements.proposer(LocalDate.of(2026, 5, 20)).get(0);

        // La déclaration ne dit ni la dose, ni la dérive, ni le vent ce jour-là.
        // « Vos abeilles ont été exposées » serait un verdict inventé — le même
        // refus qu'à l'analyse acoustique au SPRINT-31.
        assertThat(tache.libelle())
                .contains("Traitement declare", "340", "Colline", "surveiller")
                .doesNotContain("expos");
        assertThat(tache.priorite()).isEqualTo("haute");
    }

    @Test
    @DisplayName("distance inconnue : un point d'interrogation, pas un zéro")
    void distanceInconnue() {
        when(zones.ruchersExposes(anyInt(), any()))
                .thenReturn(List.of(new ExpositionBrute(1L, "Colline", 1,
                        LocalDate.of(2026, 5, 18), null)));

        // « à 0 m du rucher » se lirait « sur le rucher », qui est une affirmation.
        assertThat(traitements.proposer(LocalDate.of(2026, 5, 20)).get(0).libelle())
                .contains("? m")
                .doesNotContain("0 m");
    }

    @Test
    @DisplayName("les deux codes de règle sont stables")
    void codesStables() {
        // Les renommer orphelinerait les tâches déjà engendrées, qui ne sauraient
        // plus se justifier à l'écran. Ils font partie du contrat.
        assertThat(verification.code()).isEqualTo("verification-couvert");
        assertThat(traitements.code()).isEqualTo("zone-traitee-proche");
    }
}
