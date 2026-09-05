package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.zumm.config.PolitiqueReseau;
import com.zumm.domain.TypeIndicateur;
import com.zumm.web.dto.AnomalieReponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Client du microservice IA (lot 1 du plan de couverture).
 *
 * <p><strong>Ce qui se verifie ici est un REPLI, pas un calcul.</strong> La
 * classe promet de retomber silencieusement sur l'EWMA locale « sinon ou en cas
 * d'indisponibilite ». Tant qu'elle fabriquait son propre {@code RestClient},
 * cette promesse tenait par lecture de code — a 30,3 % de couverture.
 *
 * <p>Et le repli est la <em>seule</em> chose qui compte : un microservice absent
 * est le cas NORMAL du deploiement de reference, ou {@code ZUMM_IA_URL} peut
 * rester vide. Un client qui leverait au lieu de rendre le vide ferait echouer
 * la consultation des anomalies pour tout le monde.
 */
class ClientAnomalieIATest {

    private static final String REPONSE = """
            {"alpha":0.3,"seuilZ":3.0,"baseline":41.2,"ecartType":0.8,"nombrePoints":3,
             "anomalies":[{"instant":"2026-09-05T10:00:00Z","valeur":22.5,"zScore":4.1}]}""";

    private MockRestServiceServer serveur;

    private ClientAnomalieIA client(String url, boolean reseauAutorise) {
        RestClient.Builder constructeur = RestClient.builder();
        this.serveur = MockRestServiceServer.bindTo(constructeur).build();
        return new ClientAnomalieIA(constructeur, url, new PolitiqueReseau(reseauAutorise));
    }

    private static List<MoteurAnomalie.PointSerie> serie() {
        return List.of(
                new MoteurAnomalie.PointSerie(
                        Instant.parse("2026-09-05T08:00:00Z"), new BigDecimal("41.0")),
                new MoteurAnomalie.PointSerie(
                        Instant.parse("2026-09-05T09:00:00Z"), new BigDecimal("41.4")),
                new MoteurAnomalie.PointSerie(
                        Instant.parse("2026-09-05T10:00:00Z"), new BigDecimal("22.5")));
    }

    @Test
    @DisplayName("traduit la reponse du microservice vers le contrat du domaine")
    void scoreEtTraduit() {
        ClientAnomalieIA c = client("http://ia-service:8000", true);
        serveur.expect(requestTo("http://ia-service:8000/score"))
                .andExpect(method(HttpMethod.POST))
                // La couche anticorruption : le port parle en `PointSerie`, le
                // transport en `series[].instant`. Verifier la forme envoyee est
                // la seule facon de savoir que la traduction a bien eu lieu.
                .andExpect(jsonPath("$.series[0].instant").value("2026-09-05T08:00:00Z"))
                .andExpect(jsonPath("$.series").isArray())
                .andRespond(withSuccess(REPONSE, MediaType.APPLICATION_JSON));

        Optional<AnomalieReponse> reponse =
                c.scorer(42L, TypeIndicateur.POIDS, serie());

        assertThat(reponse).isPresent();
        AnomalieReponse r = reponse.get();
        assertThat(r.rucheId()).isEqualTo(42L);
        assertThat(r.typeIndicateur()).isEqualTo(TypeIndicateur.POIDS);
        assertThat(r.alpha()).isEqualTo(0.3);
        assertThat(r.baseline()).isEqualTo(41.2);
        assertThat(r.anomalies()).hasSize(1);
        assertThat(r.anomalies().get(0).instant())
                .isEqualTo(Instant.parse("2026-09-05T10:00:00Z"));
        assertThat(r.anomalies().get(0).zScore()).isEqualTo(4.1);
        serveur.verify();
    }

    @Test
    @DisplayName("sans URL configuree : inactif, et aucun appel emis")
    void urlAbsente() {
        ClientAnomalieIA c = client("", true);

        assertThat(c.actif()).isFalse();
        assertThat(c.scorer(42L, TypeIndicateur.POIDS, serie())).isEmpty();
        serveur.verify();
    }

    @Test
    @DisplayName("mode local : inactif meme si l'URL est configuree, et aucun appel emis")
    void modeLocal() {
        ClientAnomalieIA c = client("http://ia-service:8000", false);

        // La bascule du SPRINT-30 emprunte le chemin deja ecrit pour
        // l'indisponibilite plutot que d'en creer un second. Encore fallait-il
        // le prouver : `verify` echoue si un appel est parti.
        assertThat(c.actif()).isFalse();
        assertThat(c.scorer(42L, TypeIndicateur.POIDS, serie())).isEmpty();
        serveur.verify();
    }

    @Test
    @DisplayName("microservice en erreur : repli silencieux, jamais d'exception")
    void microserviceEnErreur() {
        ClientAnomalieIA c = client("http://ia-service:8000", true);
        serveur.expect(requestTo("http://ia-service:8000/score")).andRespond(withServerError());

        assertThat(c.scorer(42L, TypeIndicateur.POIDS, serie())).isEmpty();
    }

    @Test
    @DisplayName("une reponse sans liste d'anomalies est lue comme une liste vide")
    void reponseSansAnomalies() {
        ClientAnomalieIA c = client("http://ia-service:8000", true);
        serveur.expect(requestTo("http://ia-service:8000/score"))
                .andRespond(withSuccess("""
                        {"alpha":0.3,"seuilZ":3.0,"baseline":41.2,"ecartType":0.8,
                         "nombrePoints":3}""", MediaType.APPLICATION_JSON));

        Optional<AnomalieReponse> reponse = c.scorer(42L, TypeIndicateur.POIDS, serie());

        // Une serie saine est le cas le plus frequent : `null` y serait un piege
        // pour tout appelant qui itere sans verifier.
        assertThat(reponse).isPresent();
        assertThat(reponse.get().anomalies()).isEmpty();
    }

    @Test
    @DisplayName("actif seulement quand le reseau est ouvert ET l'URL renseignee")
    void conditionsDActivite() {
        assertThat(client("http://ia-service:8000", true).actif()).isTrue();
        assertThat(client("   ", true).actif()).isFalse();
        assertThat(client(null, true).actif()).isFalse();
        assertThat(client("http://ia-service:8000", false).actif()).isFalse();
    }
}
