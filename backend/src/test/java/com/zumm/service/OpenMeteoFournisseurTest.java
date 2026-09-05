package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zumm.config.PolitiqueReseau;
import java.time.LocalDate;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Fournisseur meteo Open-Meteo (lot 1 du plan de couverture).
 *
 * <p><strong>Pourquoi cette classe etait a 14,8 %.</strong> Elle fabriquait
 * elle-meme son {@code RestClient} : rien ne pouvait s'interposer, et la seule
 * facon de la tester aurait ete d'appeler l'API publique depuis la campagne de
 * test. Elle recoit desormais son constructeur, et ce qu'elle promet devient
 * verifiable.
 *
 * <p>Ce qu'elle promet est entierement une promesse de REPLI : « toute erreur
 * renvoie {@link Optional#empty()}, on ne fait jamais echouer une requete
 * utilisateur pour un alea reseau ». Une promesse de repli non testee est une
 * promesse dont on decouvre la faussete le jour ou le tiers tombe — c'est-a-dire
 * le seul jour ou elle comptait.
 */
class OpenMeteoFournisseurTest {

    private static final String COURANT = """
            {"current":{"temperature_2m":18.4,"relative_humidity_2m":62,"wind_speed_10m":11.3}}""";

    private MockRestServiceServer serveur;

    /** Monte le fournisseur sur un serveur simule. Aucun reseau n'est touche. */
    private OpenMeteoFournisseur fournisseur(boolean reseauAutorise) {
        RestClient.Builder constructeur = RestClient.builder();
        this.serveur = MockRestServiceServer.bindTo(constructeur).ignoreExpectOrder(true).build();
        return new OpenMeteoFournisseur(constructeur, new PolitiqueReseau(reseauAutorise));
    }

    @Test
    @DisplayName("lit les conditions courantes et les previsions de la meme reponse")
    void releveComplet() {
        OpenMeteoFournisseur f = fournisseur(true);
        serveur.expect(requestTo(Matchers.containsString("forecast_days=2")))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":18.4,"relative_humidity_2m":62,
                                    "wind_speed_10m":11.3},
                         "daily":{"time":["2026-09-05","2026-09-06"],
                                  "temperature_2m_min":[11.0,12.5],
                                  "temperature_2m_max":[23.0,25.5],
                                  "precipitation_sum":[0.0,4.2],
                                  "wind_speed_10m_max":[18.0,22.0]}}""",
                        MediaType.APPLICATION_JSON));

        Optional<FournisseurMeteo.Releve> releve = f.releve(36.8, 10.2, 2);

        assertThat(releve).isPresent();
        assertThat(releve.get().courante().temperatureCelsius()).isEqualTo(18.4);
        assertThat(releve.get().courante().humiditePourcent()).isEqualTo(62);
        assertThat(releve.get().previsions()).hasSize(2);
        assertThat(releve.get().previsions().get(1).date()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(releve.get().previsions().get(1).precipitationsMm()).isEqualTo(4.2);
        serveur.verify();
    }

    @Test
    @DisplayName("mode local : aucun appel sortant, et le repli suit le chemin d'une panne")
    void modeLocalNAppellePas() {
        OpenMeteoFournisseur f = fournisseur(false);

        assertThat(f.releve(36.8, 10.2, 3)).isEmpty();

        // Le coeur du test : `verify` sur un serveur sans attente echoue si un
        // appel a ete emis. C'est ce qui manquait au SPRINT-30 — le mode local
        // n'etait verifie que sur ce que le serveur ANNONCE, jamais sur ce qu'il
        // s'abstient de faire.
        serveur.verify();
    }

    @Test
    @DisplayName("une erreur du fournisseur rend le vide, jamais une exception")
    void erreurServeur() {
        OpenMeteoFournisseur f = fournisseur(true);
        serveur.expect(requestTo(Matchers.any(String.class))).andRespond(withServerError());

        assertThat(f.releve(36.8, 10.2, 1)).isEmpty();
    }

    @Test
    @DisplayName("une reponse sans conditions courantes rend le vide")
    void reponseSansCourant() {
        OpenMeteoFournisseur f = fournisseur(true);
        serveur.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("{\"daily\":{\"time\":[\"2026-09-05\"]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(f.releve(36.8, 10.2, 1)).isEmpty();
    }

    @Test
    @DisplayName("zero jour demande : pas de bloc journalier dans la requete")
    void aucunePrevisionDemandee() {
        OpenMeteoFournisseur f = fournisseur(true);
        serveur.expect(requestTo(Matchers.not(Matchers.containsString("forecast_days"))))
                .andRespond(withSuccess(COURANT, MediaType.APPLICATION_JSON));

        Optional<FournisseurMeteo.Releve> releve = f.releve(36.8, 10.2, 0);

        assertThat(releve).isPresent();
        assertThat(releve.get().previsions()).isEmpty();
        serveur.verify();
    }

    @Test
    @DisplayName("une demande au-dela du plafond est ramenee a 16 jours, pas refusee")
    void plafondJours() {
        OpenMeteoFournisseur f = fournisseur(true);
        serveur.expect(requestTo(Matchers.containsString("forecast_days=16")))
                .andRespond(withSuccess(COURANT, MediaType.APPLICATION_JSON));

        assertThat(f.releve(36.8, 10.2, 90)).isPresent();
        serveur.verify();
    }

    @Test
    @DisplayName("une colonne plus courte que les dates rend null, pas une erreur")
    void colonneIncomplete() {
        OpenMeteoFournisseur f = fournisseur(true);
        // Le cas que `valeur(colonne, index)` traite defensivement : la source
        // rend trois dates et deux temperatures. Sans cette lecture prudente,
        // tout le releve tomberait sur un IndexOutOfBounds — et l'apiculteur
        // perdrait AUSSI ses conditions courantes, qui elles etaient correctes.
        serveur.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":18.4,"relative_humidity_2m":62,
                                    "wind_speed_10m":11.3},
                         "daily":{"time":["2026-09-05","2026-09-06","2026-09-07"],
                                  "temperature_2m_min":[11.0,12.5],
                                  "temperature_2m_max":[23.0,25.5]}}""",
                        MediaType.APPLICATION_JSON));

        Optional<FournisseurMeteo.Releve> releve = f.releve(36.8, 10.2, 3);

        assertThat(releve).isPresent();
        assertThat(releve.get().previsions()).hasSize(3);
        assertThat(releve.get().previsions().get(2).temperatureMinCelsius()).isNull();
        assertThat(releve.get().previsions().get(2).precipitationsMm()).isNull();
        assertThat(releve.get().previsions().get(0).temperatureMinCelsius()).isEqualTo(11.0);
    }

    @Test
    @DisplayName("une date illisible est ecartee, les autres sont conservees")
    void dateIllisible() {
        OpenMeteoFournisseur f = fournisseur(true);
        serveur.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("""
                        {"current":{"temperature_2m":18.4,"relative_humidity_2m":62,
                                    "wind_speed_10m":11.3},
                         "daily":{"time":["2026-09-05","pas-une-date"],
                                  "temperature_2m_min":[11.0,12.5]}}""",
                        MediaType.APPLICATION_JSON));

        Optional<FournisseurMeteo.Releve> releve = f.releve(36.8, 10.2, 2);

        assertThat(releve).isPresent();
        assertThat(releve.get().previsions()).hasSize(1);
        assertThat(releve.get().previsions().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 5));
    }
}
