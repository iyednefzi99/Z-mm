package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.Alerte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Tache;
import com.zumm.domain.Traitement;
import com.zumm.domain.TypeIndicateur;
import com.zumm.domain.Visite;
import com.zumm.repository.AlerteRepository;
import com.zumm.repository.TacheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.Briefing;
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
 * Briefing du jour (SPRINT-30, lot 3 du plan de couverture).
 *
 * <p>Le briefing est la pièce qui tient la décision D4 de
 * l'{@code ADR-013} : <strong>il ne passe par aucun modèle de langue</strong>.
 * Il lit quatre registres et cite ce qui le fonde. Une phrase générée serait
 * plus agréable et moins vérifiable — et il faudrait envoyer l'historique de
 * l'exploitation dehors.
 *
 * <p>Ce que ces tests fixent, c'est ce que cette contrainte implique : chaque
 * ligne porte sa <em>source</em> (un identifiant de ruche, une date, un
 * produit), et non une formulation. Si le détail d'une ligne cessait d'être
 * vérifiable, le briefing redeviendrait ce qu'il refuse d'être.
 */
@ExtendWith(MockitoExtension.class)
class BriefingServiceTest {

    @Mock private AlerteRepository alertes;
    @Mock private TacheRepository taches;
    @Mock private TraitementRepository traitements;
    @Mock private VisiteRepository visites;

    private BriefingService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 9, 5);

    @BeforeEach
    void monter() {
        service = new BriefingService(alertes, taches, traitements, visites);
        lenient().when(alertes.findByOuverteTrueOrderByOuverteLeDesc()).thenReturn(List.of());
        lenient().when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(any()))
                .thenReturn(List.of());
        lenient().when(traitements.sousCarenceAu(any())).thenReturn(List.of());
        lenient().when(visites.dernieresVisitesParRuche()).thenReturn(List.of());
    }

    private static Ruche ruche(long id) {
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(id);
        return r;
    }

    private static Alerte alerte(Ruche ruche, TypeIndicateur type, String valeur) {
        Alerte a = mock(Alerte.class);
        lenient().when(a.getRuche()).thenReturn(ruche);
        lenient().when(a.getTypeIndicateur()).thenReturn(type);
        lenient().when(a.getValeurDeclenchement()).thenReturn(new BigDecimal(valeur));
        return a;
    }

    private static Tache tache(String libelle, LocalDate echeance, Ruche ruche) {
        Tache t = mock(Tache.class);
        lenient().when(t.getLibelle()).thenReturn(libelle);
        lenient().when(t.getEcheance()).thenReturn(echeance);
        lenient().when(t.getRuche()).thenReturn(ruche);
        return t;
    }

    private static Traitement traitement(Ruche ruche, String produit, LocalDate debut,
            LocalDate retrait) {
        Traitement t = mock(Traitement.class);
        lenient().when(t.getRuche()).thenReturn(ruche);
        lenient().when(t.getProduit()).thenReturn(produit);
        lenient().when(t.getDateDebut()).thenReturn(debut);
        lenient().when(t.getDateRetrait()).thenReturn(retrait);
        return t;
    }

    private static Visite visite(Ruche ruche, LocalDate date) {
        Visite v = mock(Visite.class);
        lenient().when(v.getRuche()).thenReturn(ruche);
        lenient().when(v.getDateVisite()).thenReturn(date);
        return v;
    }

    // ── Rien à dire ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("une journée sans rien à signaler rend un briefing vide, pas une phrase")
    void journeeCalme() {
        Briefing briefing = service.duJour(JOUR);

        // Un briefing qui trouverait toujours quelque chose à dire cesserait
        // d'être lu. Le vide est une information.
        assertThat(briefing.genereLe()).isEqualTo(JOUR);
        assertThat(briefing.lignes()).isEmpty();
    }

    // ── Alertes ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("chaque alerte ouverte a sa ligne, et cite la valeur déclenchante")
    void alertesOuvertes() {
        Ruche r42 = ruche(42L);
        Ruche r43 = ruche(43L);
        Alerte a1 = alerte(r42, TypeIndicateur.POIDS, "12.5");
        Alerte a2 = alerte(r43, TypeIndicateur.TEMPERATURE, "38.2");
        when(alertes.findByOuverteTrueOrderByOuverteLeDesc()).thenReturn(List.of(a1, a2));

        List<Briefing.Ligne> lignes = service.duJour(JOUR).lignes();

        // La ligne cite CE QUI la fonde : sans la valeur, l'apiculteur devrait
        // ouvrir l'écran des alertes pour savoir s'il doit se déplacer.
        assertThat(lignes).hasSize(2);
        assertThat(lignes).allSatisfy(l -> assertThat(l.categorie()).isEqualTo("alerte"));
        assertThat(lignes.get(0).titre()).contains("#42");
        assertThat(lignes.get(0).detail()).contains("12.5");
        assertThat(lignes.get(0).rucheId()).isEqualTo(42L);
    }

    // ── Tâches ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("les tâches échues tiennent en UNE ligne, avec la plus ancienne nommée")
    void tachesEchuesGroupees() {
        Ruche r7 = ruche(7L);
        Tache ancienne = tache("Retirer les hausses", LocalDate.of(2026, 8, 1), r7);
        Tache recente = tache("Poser un piège", LocalDate.of(2026, 9, 1), null);
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(ancienne, recente));

        List<Briefing.Ligne> lignes = service.duJour(JOUR).lignes();

        // Une exploitation qui a quarante tâches en retard n'a pas quarante
        // choses à savoir, elle en a une. Le détail vit dans l'écran des tâches.
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).categorie()).isEqualTo("tache");
        assertThat(lignes.get(0).titre()).contains("2 tache(s)");
        assertThat(lignes.get(0).detail()).contains("Retirer les hausses")
                .contains("2026-08-01");
        assertThat(lignes.get(0).rucheId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("une tâche échue sans ruche ne fabrique pas de rattachement")
    void tacheSansRuche() {
        Tache sansRuche = tache("Commander de la cire", LocalDate.of(2026, 8, 1), null);
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(sansRuche));

        assertThat(service.duJour(JOUR).lignes().get(0).rucheId()).isNull();
    }

    // ── Carences ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("une carence qui se termine dans la semaine est annoncée")
    void carenceProche() {
        Ruche r42 = ruche(42L);
        Traitement t = traitement(r42, "Apivar", LocalDate.of(2026, 8, 1),
                JOUR.plusDays(3));
        when(traitements.sousCarenceAu(JOUR)).thenReturn(List.of(t));

        List<Briefing.Ligne> lignes = service.duJour(JOUR).lignes();

        // Savoir qu'une ruche est SOUS carence est facile ; savoir qu'elle en
        // SORT jeudi demande de calculer, et c'est ce qui décide d'une tournée.
        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).categorie()).isEqualTo("carence");
        assertThat(lignes.get(0).titre()).contains("#42").contains(JOUR.plusDays(3).toString());
        assertThat(lignes.get(0).detail()).contains("Apivar").contains("2026-08-01");
    }

    @Test
    @DisplayName("une carence qui se termine au-delà de la semaine n'encombre pas")
    void carenceLointaine() {
        Ruche r42 = ruche(42L);
        Traitement t = traitement(r42, "Apivar", LocalDate.of(2026, 8, 1), JOUR.plusDays(8));
        when(traitements.sousCarenceAu(JOUR)).thenReturn(List.of(t));

        assertThat(service.duJour(JOUR).lignes()).isEmpty();
    }

    @Test
    @DisplayName("le septième jour est encore dans la fenêtre")
    void bordDeLaFenetreDeCarence() {
        Ruche r42 = ruche(42L);
        Traitement t = traitement(r42, "Apivar", LocalDate.of(2026, 8, 1), JOUR.plusDays(7));
        when(traitements.sousCarenceAu(JOUR)).thenReturn(List.of(t));

        assertThat(service.duJour(JOUR).lignes()).hasSize(1);
    }

    @Test
    @DisplayName("un traitement sans date de retrait n'est pas annoncé")
    void carenceSansDateDeRetrait() {
        Ruche r42 = ruche(42L);
        Traitement t = traitement(r42, "Apivar", LocalDate.of(2026, 8, 1), null);
        when(traitements.sousCarenceAu(JOUR)).thenReturn(List.of(t));

        // Sans date de retrait, il n'y a rien à annoncer : inventer un horizon
        // ferait sortir des hausses trop tôt.
        assertThat(service.duJour(JOUR).lignes()).isEmpty();
    }

    // ── Colonies non vues ───────────────────────────────────────────────────

    @Test
    @DisplayName("une colonie non vue depuis trois semaines est signalée, avec son ancienneté")
    void colonieNonVue() {
        Ruche r42 = ruche(42L);
        Visite v = visite(r42, JOUR.minusDays(30));
        when(visites.dernieresVisitesParRuche()).thenReturn(List.of(v));

        List<Briefing.Ligne> lignes = service.duJour(JOUR).lignes();

        assertThat(lignes).hasSize(1);
        assertThat(lignes.get(0).categorie()).isEqualTo("visite");
        assertThat(lignes.get(0).titre()).contains("#42");
        assertThat(lignes.get(0).detail()).isEqualTo("30 jours");
    }

    @Test
    @DisplayName("une colonie vue il y a exactement trois semaines n'est pas signalée")
    void bordDesTroisSemaines() {
        Ruche r42 = ruche(42L);
        Visite v = visite(r42, JOUR.minusDays(21));
        when(visites.dernieresVisitesParRuche()).thenReturn(List.of(v));

        // Trois semaines pile : c'est l'intervalle au-delà duquel une colonie
        // peut avoir essaimé sans que personne ne l'ait su. Le jour même, non.
        assertThat(service.duJour(JOUR).lignes()).isEmpty();
    }

    @Test
    @DisplayName("une ruche JAMAIS visitée n'apparaît pas : ce serait un reproche")
    void rucheJamaisVisitee() {
        // `dernieresVisitesParRuche` ne rend rien pour une ruche sans visite.
        // Elle vient peut-être d'être enregistrée, et la signaler le jour de sa
        // création ferait passer le briefing pour un reproche.
        assertThat(service.duJour(JOUR).lignes()).isEmpty();
    }

    // ── Ordre ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("les lignes sortent par urgence, puis par catégorie")
    void ordreStable() {
        Ruche r42 = ruche(42L);
        Alerte a = alerte(r42, TypeIndicateur.POIDS, "12.5");
        Tache t = tache("Retirer les hausses", LocalDate.of(2026, 8, 1), r42);
        Traitement tr = traitement(r42, "Apivar", LocalDate.of(2026, 8, 1), JOUR.plusDays(2));
        Visite v = visite(r42, JOUR.minusDays(30));
        when(alertes.findByOuverteTrueOrderByOuverteLeDesc()).thenReturn(List.of(a));
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(t));
        when(traitements.sousCarenceAu(JOUR)).thenReturn(List.of(tr));
        when(visites.dernieresVisitesParRuche()).thenReturn(List.of(v));

        List<Briefing.Ligne> lignes = service.duJour(JOUR).lignes();

        // Un briefing dont l'ordre change d'un jour à l'autre se relit en entier
        // à chaque fois. Urgence 1 : alerte puis tâche (alphabétique à égalité).
        assertThat(lignes).extracting(Briefing.Ligne::categorie)
                .containsExactly("alerte", "tache", "carence", "visite");
    }
}
