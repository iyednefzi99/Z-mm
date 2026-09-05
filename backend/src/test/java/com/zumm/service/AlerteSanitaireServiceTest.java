package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.configmetier.SeuilsMetier;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.EtatSante;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.AlerteSanitaire;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Alertes sanitaires (US-041, lot 3 du plan de couverture).
 *
 * <p>82,6 % d'instructions mais <strong>46,2 % de branches</strong>, et la
 * javadoc du service prévient : <em>« l'ordre des branches EST la règle
 * métier — le modifier change le comportement, pas seulement la lisibilité »</em>.
 * Une règle qui tient dans l'ordre d'une cascade de {@code else if} et que rien
 * ne teste est une règle qu'un refactor bien intentionné défait sans bruit.
 *
 * <p>Ces tests fixent la <strong>priorité</strong> : un mauvais état l'emporte
 * sur un retard de visite, et un retard l'emporte sur un état moyen. C'est
 * l'ordre du travail de l'apiculteur, pas celui du modèle de données.
 */
@ExtendWith(MockitoExtension.class)
class AlerteSanitaireServiceTest {

    @Mock private VisiteRepository visites;
    @Mock private RucheRepository ruches;
    @Mock private ConfigurationMetier configuration;

    private AlerteSanitaireService service;

    private static final int DELAI = 21;

    @BeforeEach
    void monter() {
        service = new AlerteSanitaireService(visites, ruches, configuration);
        SeuilsMetier seuils = mock(SeuilsMetier.class);
        lenient().when(seuils.delaiAlerteJours()).thenReturn(DELAI);
        lenient().when(configuration.seuils()).thenReturn(seuils);
        lenient().when(visites.dernieresVisitesParRuche()).thenReturn(List.of());
        lenient().when(ruches.findAll()).thenReturn(List.of());
    }

    private static Ruche ruche(long id, String modele) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        lenient().when(r.getModele()).thenReturn(modele);
        lenient().when(r.getEtat()).thenReturn(EtatRuche.ACTIVE);
        return r;
    }

    private static Visite visite(Ruche ruche, int joursAvant, EtatSante etat) {
        Visite v = mock(Visite.class);
        lenient().when(v.getRuche()).thenReturn(ruche);
        lenient().when(v.getDateVisite()).thenReturn(LocalDate.now().minusDays(joursAvant));
        lenient().when(v.getEtatSante()).thenReturn(etat);
        return v;
    }

    /** Une ruche, sa dernière visite, et l'alerte qui en sort. */
    private AlerteSanitaire alerteDe(int joursAvant, EtatSante etat) {
        Ruche r = ruche(1L, "Dadant");
        Visite v = visite(r, joursAvant, etat);
        when(ruches.findAll()).thenReturn(List.of(r));
        when(visites.dernieresVisitesParRuche()).thenReturn(List.of(v));
        return service.alertesSanitaires().get(0);
    }

    // ── Absence de visite ───────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche jamais visitée est CRITIQUE, pas silencieuse")
    void jamaisVisitee() {
        Ruche r = ruche(1L, "Dadant");
        when(ruches.findAll()).thenReturn(List.of(r));

        AlerteSanitaire alerte = service.alertesSanitaires().get(0);

        // Une colonie qu'on n'a jamais ouverte est le pire cas : ne rien savoir
        // n'est pas rassurant, et l'absence de donnée ne doit pas produire
        // l'absence d'alerte.
        assertThat(alerte.niveau()).isEqualTo(AlerteSanitaire.CRITIQUE);
        assertThat(alerte.motif()).isEqualTo("Aucune visite enregistrée");
        assertThat(alerte.derniereVisite()).isNull();
        assertThat(alerte.joursDepuisVisite()).isNull();
        assertThat(alerte.dernierEtatSante()).isNull();
    }

    // ── La cascade, dans son ordre ──────────────────────────────────────────

    @Test
    @DisplayName("un état MAUVAIS est critique, même si la visite est d'hier")
    void mauvaisEtatPrimeSurLaFraicheur() {
        AlerteSanitaire alerte = alerteDe(1, EtatSante.MAUVAIS);

        assertThat(alerte.niveau()).isEqualTo(AlerteSanitaire.CRITIQUE);
        assertThat(alerte.motif()).contains("mauvais");
    }

    @Test
    @DisplayName("un état MAUVAIS l'emporte sur un retard de visite")
    void mauvaisEtatPrimeSurLeRetard() {
        AlerteSanitaire alerte = alerteDe(60, EtatSante.MAUVAIS);

        // Les deux conditions sont vraies. L'ordre décide, et il décide bien :
        // « colonie malade » est plus urgent que « colonie non vue ».
        assertThat(alerte.niveau()).isEqualTo(AlerteSanitaire.CRITIQUE);
        assertThat(alerte.motif()).contains("mauvais").doesNotContain("Aucune visite depuis");
    }

    @Test
    @DisplayName("un retard de visite l'emporte sur un état moyen")
    void retardPrimeSurEtatMoyen() {
        AlerteSanitaire alerte = alerteDe(60, EtatSante.MOYEN);

        // Un état moyen vu il y a deux mois n'est plus une observation : c'est
        // un souvenir. Le motif affiché doit donc être le retard.
        assertThat(alerte.niveau()).isEqualTo(AlerteSanitaire.ATTENTION);
        assertThat(alerte.motif()).contains("Aucune visite depuis 60 jours")
                .contains("seuil " + DELAI);
    }

    @Test
    @DisplayName("un état moyen récent est une attention, pas une critique")
    void etatMoyenRecent() {
        AlerteSanitaire alerte = alerteDe(3, EtatSante.MOYEN);

        assertThat(alerte.niveau()).isEqualTo(AlerteSanitaire.ATTENTION);
        assertThat(alerte.motif()).isEqualTo("État sanitaire moyen à surveiller");
    }

    @Test
    @DisplayName("un bon état récent ne déclenche rien")
    void bonEtatRecent() {
        AlerteSanitaire alerte = alerteDe(3, EtatSante.BON);

        assertThat(alerte.niveau()).isEqualTo(AlerteSanitaire.OK);
        assertThat(alerte.motif()).isEqualTo("État sanitaire satisfaisant");
        assertThat(alerte.joursDepuisVisite()).isEqualTo(3);
    }

    @Test
    @DisplayName("une visite sans état sanitaire noté ne vaut pas une alerte")
    void etatNonRenseigne() {
        AlerteSanitaire alerte = alerteDe(3, null);

        // `null` n'est ni MAUVAIS ni MOYEN : la cascade tombe sur le cas
        // satisfaisant. C'est discutable, et c'est le comportement en place —
        // le fixer ici rend le choix visible si quelqu'un veut le changer.
        assertThat(alerte.niveau()).isEqualTo(AlerteSanitaire.OK);
        assertThat(alerte.dernierEtatSante()).isNull();
    }

    @Test
    @DisplayName("le seuil est strict : à J+21 rien, à J+22 une attention")
    void bordDuSeuil() {
        assertThat(alerteDe(DELAI, EtatSante.BON).niveau()).isEqualTo(AlerteSanitaire.OK);

        monter();
        assertThat(alerteDe(DELAI + 1, EtatSante.BON).niveau())
                .isEqualTo(AlerteSanitaire.ATTENTION);
    }

    @Test
    @DisplayName("le seuil vient de la configuration, pas d'une constante")
    void seuilConfigurable() {
        SeuilsMetier seuils = mock(SeuilsMetier.class);
        when(seuils.delaiAlerteJours()).thenReturn(90);
        when(configuration.seuils()).thenReturn(seuils);

        // Un apiculteur transhumant ne visite pas au même rythme qu'un rucher
        // de jardin : le seuil est un réglage d'exploitation.
        assertThat(alerteDe(60, EtatSante.BON).niveau()).isEqualTo(AlerteSanitaire.OK);
    }

    // ── Ordre ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("les ruches critiques passent devant, les saines ferment la liste")
    void ordreDesAlertes() {
        Ruche saine = ruche(1L, "Saine");
        Ruche moyenne = ruche(2L, "Moyenne");
        Ruche malade = ruche(3L, "Malade");
        Visite vSaine = visite(saine, 2, EtatSante.BON);
        Visite vMoyenne = visite(moyenne, 2, EtatSante.MOYEN);
        Visite vMalade = visite(malade, 2, EtatSante.MAUVAIS);
        when(ruches.findAll()).thenReturn(List.of(saine, moyenne, malade));
        when(visites.dernieresVisitesParRuche()).thenReturn(List.of(vSaine, vMoyenne, vMalade));

        List<AlerteSanitaire> alertes = service.alertesSanitaires();

        // L'écran se lit de haut en bas, et l'apiculteur s'arrête où il n'a plus
        // le temps : ce qui compte doit être en haut.
        assertThat(alertes).extracting(AlerteSanitaire::niveau)
                .containsExactly(AlerteSanitaire.CRITIQUE, AlerteSanitaire.ATTENTION,
                        AlerteSanitaire.OK);
        assertThat(alertes).extracting(AlerteSanitaire::rucheModele)
                .containsExactly("Malade", "Moyenne", "Saine");
    }

    @Test
    @DisplayName("un parc vide rend une liste vide, pas une erreur")
    void parcVide() {
        assertThat(service.alertesSanitaires()).isEmpty();
    }
}
