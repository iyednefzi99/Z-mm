package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zumm.domain.Consommable;
import com.zumm.domain.Ferme;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.Tache;
import com.zumm.repository.TacheRepository;
import com.zumm.web.dto.EtapeTournee;
import com.zumm.web.dto.FeuilleChargement;
import com.zumm.web.dto.TourneeReponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Feuille de chargement de la tournée (SPRINT-33, lot K).
 *
 * <p>Ce service <strong>n'invente rien, il assemble</strong> — la tournée du
 * SPRINT-10, les tâches du SPRINT-05, le stock du SPRINT-27. Ce qui se teste
 * ici est donc l'assemblage, et surtout les trois endroits où il aurait pu
 * mentir :
 *
 * <ol>
 *   <li>un besoin <strong>non chiffré</strong> reste {@code null} et non zéro —
 *       « 0 kg de candi » fait partir sans ;
 *   <li>un besoin non chiffré ne déclare <strong>jamais</strong> un manque : on
 *       ne peut pas manquer d'une quantité qu'on n'a pas exprimée ;
 *   <li>les tâches <strong>sans consommable</strong> figurent quand même à
 *       l'étape — une feuille qui n'afficherait que le matériel laisserait croire
 *       qu'il n'y a rien d'autre à faire sur place.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ChargementServiceTest {

    @Mock private PlanningService plannings;
    @Mock private TacheRepository taches;

    private ChargementService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 4, 12);

    @BeforeEach
    void monter() {
        service = new ChargementService(plannings, taches);
    }

    private static Site site(long id, String nom) {
        Site site = new Site(nom, new Ferme("Ferme", null), new BigDecimal("48.1"),
                new BigDecimal("2.3"), null);
        ReflectionTestUtils.setField(site, "id", id);
        return site;
    }

    private static Ruche ruche(Site site) {
        Ruche ruche = new Ruche("Dadant", site, site.getFerme(), com.zumm.domain.EtatRuche.ACTIVE);
        ReflectionTestUtils.setField(ruche, "id", 100L + site.getId());
        return ruche;
    }

    private static Consommable consommable(long id, String libelle, String stock) {
        Consommable consommable = new Consommable(libelle, "candi", "kg");
        ReflectionTestUtils.setField(consommable, "id", id);
        consommable.setQuantite(new BigDecimal(stock));
        return consommable;
    }

    private static Tache tache(String libelle, Site site, Consommable consommable,
            String quantite) {
        Tache tache = new Tache(libelle);
        tache.setRuche(ruche(site));
        tache.setConsommable(consommable);
        tache.setQuantitePrevue(quantite == null ? null : new BigDecimal(quantite));
        return tache;
    }

    private void tournee(Site... sites) {
        List<EtapeTournee> etapes = new java.util.ArrayList<>();
        for (int rang = 0; rang < sites.length; rang++) {
            etapes.add(new EtapeTournee(rang + 1, sites[rang].getId(), sites[rang].getNom(),
                    null, null, List.of(), 2, BigDecimal.ZERO));
        }
        when(plannings.tournee(3L, JOUR, null)).thenReturn(new TourneeReponse(
                3L, "Nadia", JOUR, sites.length, sites.length * 2, BigDecimal.ZERO, etapes));
    }

    // ─── L'assemblage ───────────────────────────────────────────────────────

    @Test
    @DisplayName("les besoins se consolident sur toute la tournée, dans l'ordre des étapes")
    void consolidationSurLaTournee() {
        Site colline = site(1L, "Colline");
        Site vallon = site(2L, "Vallon");
        Consommable candi = consommable(10L, "Candi", "40");
        tournee(colline, vallon);
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(
                        tache("Nourrir 12", colline, candi, "9"),
                        tache("Nourrir 13", vallon, candi, "6")));

        FeuilleChargement feuille = service.pour(3L, JOUR, null);

        assertThat(feuille.etapes()).hasSize(2);
        // Par étape : quoi déposer où.
        assertThat(feuille.etapes().get(0).besoins().get(0).requis())
                .isEqualByComparingTo("9");
        // Consolidé : quoi charger. Sans cette seconde lecture, l'apiculteur
        // additionne quinze lignes de tête sur le pas de la porte.
        assertThat(feuille.besoins()).hasSize(1);
        assertThat(feuille.besoins().get(0).requis()).isEqualByComparingTo("15");
        assertThat(feuille.besoins().get(0).enStock()).isEqualByComparingTo("40");
        assertThat(feuille.besoins().get(0).suffisant()).isTrue();
        assertThat(feuille.manquants()).isZero();
    }

    @Test
    @DisplayName("le manque est NOMMÉ, pas corrigé")
    void manqueNomme() {
        Site colline = site(1L, "Colline");
        Consommable candi = consommable(10L, "Candi", "5");
        tournee(colline);
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(tache("Nourrir 12", colline, candi, "9")));

        FeuilleChargement feuille = service.pour(3L, JOUR, null);

        // Décider quelle ruche sauter est une décision d'exploitation : elle ne
        // se prend pas dans un calcul. La feuille signale et s'arrête là.
        assertThat(feuille.manquants()).isEqualTo(1);
        assertThat(feuille.besoins().get(0).suffisant()).isFalse();
        assertThat(feuille.besoins().get(0).requis()).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("un besoin non chiffré reste null, jamais zéro, et ne déclare aucun manque")
    void besoinNonChiffre() {
        Site colline = site(1L, "Colline");
        Consommable candi = consommable(10L, "Candi", "0");
        tournee(colline);
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(tache("Prendre du candi", colline, candi, null)));

        FeuilleChargement feuille = service.pour(3L, JOUR, null);

        // « 0 kg de candi » ferait partir sans. La vérité est « il en faut, on ne
        // sait pas combien ».
        assertThat(feuille.besoins().get(0).requis()).isNull();
        // Et on ne peut pas manquer d'une quantité qu'on n'a pas exprimée : le
        // déclarer insuffisant par prudence ferait clignoter la feuille entière.
        assertThat(feuille.besoins().get(0).suffisant()).isTrue();
        assertThat(feuille.manquants()).isZero();
    }

    @Test
    @DisplayName("une tâche chiffrée et une non chiffrée sur le même consommable s'additionnent")
    void melangeChiffreEtNonChiffre() {
        Site colline = site(1L, "Colline");
        Consommable candi = consommable(10L, "Candi", "40");
        tournee(colline);
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(
                        tache("Nourrir 12", colline, candi, "9"),
                        tache("Prendre du candi", colline, candi, null)));

        // Le total reste celui des tâches CHIFFRÉES : ajouter un zéro pour la
        // seconde donnerait un chiffre juste par hasard et faux par principe.
        assertThat(service.pour(3L, JOUR, null).besoins().get(0).requis())
                .isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("les tâches sans consommable figurent quand même à l'étape")
    void tachesSansConsommable() {
        Site colline = site(1L, "Colline");
        tournee(colline);
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(tache("Controler la ponte", colline, null, null)));

        FeuilleChargement feuille = service.pour(3L, JOUR, null);

        // Une feuille qui n'afficherait que le matériel laisserait croire qu'il
        // n'y a rien d'autre à faire sur place.
        assertThat(feuille.etapes().get(0).taches()).containsExactly("Controler la ponte");
        assertThat(feuille.etapes().get(0).besoins()).isEmpty();
        assertThat(feuille.besoins()).isEmpty();
    }

    @Test
    @DisplayName("une tâche d'exploitation, sans ruche, n'entre dans aucune étape")
    void tacheSansRuche() {
        Site colline = site(1L, "Colline");
        tournee(colline);
        Tache administrative = new Tache("Declarer les ruches");
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of(administrative));

        // Elle ne se rattache à aucun rucher : la rattacher au premier de la
        // tournée serait arbitraire, et la ferait disparaître un autre jour.
        assertThat(service.pour(3L, JOUR, null).etapes().get(0).taches()).isEmpty();
    }

    @Test
    @DisplayName("une tournée vide rend une feuille vide, pas une erreur")
    void tourneeVide() {
        when(plannings.tournee(3L, JOUR, null)).thenReturn(new TourneeReponse(
                3L, "Nadia", JOUR, 0, 0, BigDecimal.ZERO, List.of()));
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(JOUR))
                .thenReturn(List.of());

        FeuilleChargement feuille = service.pour(3L, JOUR, null);

        assertThat(feuille.etapes()).isEmpty();
        assertThat(feuille.besoins()).isEmpty();
        assertThat(feuille.nombreSites()).isZero();
        assertThat(feuille.agentNom()).isEqualTo("Nadia");
    }

    @Test
    @DisplayName("le site de départ imposé est transmis à la tournée, pas réinterprété")
    void departTransmis() {
        when(plannings.tournee(3L, JOUR, 2L)).thenReturn(new TourneeReponse(
                3L, "Nadia", JOUR, 0, 0, BigDecimal.ZERO, List.of()));
        when(taches.findByFaiteFalseAndEcheanceLessThanEqualOrderByEcheanceAsc(any()))
                .thenReturn(List.of());

        service.pour(3L, JOUR, 2L);

        org.mockito.Mockito.verify(plannings).tournee(3L, JOUR, 2L);
    }
}
