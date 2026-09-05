package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zumm.domain.Nourrissement;
import com.zumm.domain.Recolte;
import com.zumm.domain.Traitement;
import com.zumm.repository.NourrissementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.web.dto.DossierConformite;
import com.zumm.web.dto.DossierConformite.PointControle;
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
 * Dossier de conformité biologique (SPRINT-29, lot 3 du plan de couverture).
 *
 * <p>74 % d'instructions mais <strong>42,3 % de branches</strong>. Chaque
 * branche non couverte est ici un <em>verdict</em> rendu à un contrôleur : les
 * trois états ne se valent pas, et se tromper d'état est plus grave que de se
 * tromper de nombre.
 *
 * <p><strong>La règle que ces tests verrouillent tient en une phrase :</strong>
 * Zümm ne certifie rien, et nomme ce qu'il ne peut pas vérifier. L'origine des
 * sucres sort « à justifier » <em>même quand tout est tracé</em> — la déclarer
 * conforme parce que la ligne existe serait exactement le mensonge que ce
 * dossier doit éviter. Symétriquement, une substance inconnue est
 * <em>signalée</em>, jamais déclarée interdite : une spécialité autorisée
 * localement peut ne pas figurer dans notre liste.
 */
@ExtendWith(MockitoExtension.class)
class ConformiteBioServiceTest {

    @Mock private TraitementRepository traitements;
    @Mock private NourrissementRepository nourrissements;
    @Mock private RecolteRepository recoltes;

    private ConformiteBioService service;

    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void monter() {
        service = new ConformiteBioService(traitements, nourrissements, recoltes);
        lenient().when(traitements.findAll()).thenReturn(List.of());
        lenient().when(nourrissements.findAll()).thenReturn(List.of());
        lenient().when(recoltes.findAll()).thenReturn(List.of());
    }

    private static Traitement traitement(String substance, LocalDate date) {
        Traitement t = new Traitement(null, null, "Apivar", "varroa", date);
        t.setSubstanceActive(substance);
        return t;
    }

    private static Recolte recolte(LocalDate date, String lot, boolean carenceForcee) {
        Recolte r = new Recolte(null, date, new BigDecimal("18.0"), lot);
        if (carenceForcee) {
            // Le forcage ne se pose pas par un setter : il exige un motif, et
            // c'est ce qui le rend opposable (SPRINT-22).
            r.forcerCarence("miellee exceptionnelle, hausses deja retirees");
        }
        return r;
    }

    private PointControle point(String code) {
        DossierConformite dossier = service.evaluer(DEBUT, FIN);
        return dossier.points().stream()
                .filter(p -> p.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("point absent : " + code));
    }

    // ── L'avertissement ─────────────────────────────────────────────────────

    @Test
    @DisplayName("le dossier dit d'entrée qu'il ne certifie pas, et porte ses quatre points")
    void avertissementEtStructure() {
        DossierConformite dossier = service.evaluer(DEBUT, FIN);

        assertThat(dossier.avertissement()).contains("ne certifie pas")
                .contains("organisme agréé");
        assertThat(dossier.debut()).isEqualTo(DEBUT);
        assertThat(dossier.fin()).isEqualTo(FIN);
        assertThat(dossier.points()).extracting(PointControle::code)
                .containsExactly("traitements", "carences", "nourrissement", "tracabilite");
    }

    // ── Traitements ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("un registre de traitements VIDE est à justifier, pas vérifié")
    void registreVide() {
        PointControle p = point("traitements");

        // « Rien à signaler » et « rien d'enregistré » se ressemblent à l'écran
        // et ne se ressemblent pas devant un contrôle : un registre vide se
        // justifie, il ne se présume pas.
        assertThat(p.statut()).isEqualTo("a_justifier");
        assertThat(p.nombre()).isZero();
        assertThat(p.detail()).contains("ne se présume pas");
    }

    @Test
    @DisplayName("des substances toutes admises sont vérifiées")
    void substancesAdmises() {
        when(traitements.findAll()).thenReturn(List.of(
                traitement("acide oxalique", LocalDate.of(2026, 3, 1)),
                traitement("Thymol", LocalDate.of(2026, 8, 1))));

        PointControle p = point("traitements");

        // La comparaison porte sur la substance ACTIVE et sans tenir compte de
        // la casse : un nom commercial change, une molécule non.
        assertThat(p.statut()).isEqualTo("verifie");
        assertThat(p.nombre()).isEqualTo(2);
    }

    @Test
    @DisplayName("une substance inconnue est SIGNALÉE, jamais déclarée interdite")
    void substanceHorsListe() {
        when(traitements.findAll()).thenReturn(List.of(
                traitement("amitraze", LocalDate.of(2026, 8, 1)),
                traitement("thymol", LocalDate.of(2026, 3, 1))));

        PointControle p = point("traitements");

        assertThat(p.statut()).isEqualTo("signale");
        assertThat(p.nombre()).isEqualTo(1);
        assertThat(p.detail()).contains("amitraze");
        // Le mot compte : c'est au contrôleur de trancher, pas au logiciel.
        assertThat(p.detail()).contains("c'est au contrôleur de trancher");
    }

    @Test
    @DisplayName("une substance non renseignée est signalée avec le nom du produit")
    void substanceNonRenseignee() {
        when(traitements.findAll())
                .thenReturn(List.of(traitement(null, LocalDate.of(2026, 8, 1))));

        PointControle p = point("traitements");

        // Sans substance, on ne peut rien vérifier : le taire reviendrait à
        // classer comme conforme ce qu'on n'a pas regardé.
        assertThat(p.statut()).isEqualTo("signale");
        assertThat(p.detail()).contains("Apivar").contains("substance non renseignée");
    }

    @Test
    @DisplayName("une substance vide compte comme non admise")
    void substanceVide() {
        when(traitements.findAll())
                .thenReturn(List.of(traitement("   ", LocalDate.of(2026, 8, 1))));

        assertThat(point("traitements").statut()).isEqualTo("signale");
    }

    @Test
    @DisplayName("un traitement hors période n'entre pas dans le dossier")
    void horsPeriode() {
        when(traitements.findAll()).thenReturn(List.of(
                traitement("amitraze", LocalDate.of(2025, 8, 1)),
                traitement("amitraze", LocalDate.of(2027, 1, 1))));

        // Un dossier porte sur une période : y verser d'autres années ferait
        // signaler une non-conformité déjà close.
        assertThat(point("traitements").statut()).isEqualTo("a_justifier");
    }

    @Test
    @DisplayName("un traitement sans date n'entre pas dans le dossier")
    void traitementSansDate() {
        when(traitements.findAll()).thenReturn(List.of(traitement("amitraze", null)));

        assertThat(point("traitements").statut()).isEqualTo("a_justifier");
    }

    @Test
    @DisplayName("les bornes de la période sont incluses")
    void bornesIncluses() {
        when(traitements.findAll()).thenReturn(List.of(
                traitement("thymol", DEBUT), traitement("thymol", FIN)));

        assertThat(point("traitements").nombre()).isEqualTo(2);
    }

    // ── Carences ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aucune récolte forcée : vérifié")
    void aucuneCarenceForcee() {
        when(recoltes.findAll()).thenReturn(List.of(
                recolte(LocalDate.of(2026, 7, 1), "L-1", false)));

        PointControle p = point("carences");

        assertThat(p.statut()).isEqualTo("verifie");
        assertThat(p.nombre()).isZero();
    }

    @Test
    @DisplayName("une récolte forcée est signalée, avec renvoi au journal d'audit")
    void carenceForcee() {
        when(recoltes.findAll()).thenReturn(List.of(
                recolte(LocalDate.of(2026, 7, 1), "L-1", true),
                recolte(LocalDate.of(2026, 8, 1), "L-2", false)));

        PointControle p = point("carences");

        // La seule non-conformité que le système constate de lui-même, et il ne
        // le peut que parce que le SPRINT-22 a rendu le forçage traçable au lieu
        // de l'interdire.
        assertThat(p.statut()).isEqualTo("signale");
        assertThat(p.nombre()).isEqualTo(1);
        assertThat(p.detail()).contains("forcage");
    }

    // ── Origine des sucres ──────────────────────────────────────────────────

    @Test
    @DisplayName("l'origine des sucres reste À JUSTIFIER même quand tout est tracé")
    void origineDesSucresToujoursAJustifier() {
        when(nourrissements.findAll()).thenReturn(List.of(
                new Nourrissement(null, null, LocalDate.of(2026, 9, 1),
                        "sirop 1:1", new BigDecimal("2.5"), "L"),
                new Nourrissement(null, null, LocalDate.of(2026, 9, 15),
                        "candi", new BigDecimal("1"), "kg")));

        PointControle p = point("nourrissement");

        // Deux apports parfaitement tracés, et le point reste « à justifier » :
        // le système enregistre CE QUI a été donné, jamais d'où cela vient. Le
        // déclarer conforme parce que la ligne existe serait le mensonge que ce
        // dossier doit éviter.
        assertThat(p.statut()).isEqualTo("a_justifier");
        assertThat(p.nombre()).isEqualTo(2);
        assertThat(p.detail()).contains("se prouve par facture");
    }

    // ── Traçabilité ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("chaque récolte porte son lot : vérifié")
    void tousLesLotsPresents() {
        when(recoltes.findAll()).thenReturn(List.of(
                recolte(LocalDate.of(2026, 7, 1), "L-1", false)));

        assertThat(point("tracabilite").statut()).isEqualTo("verifie");
    }

    @Test
    @DisplayName("un lot absent ou vide interrompt la chaîne du pot à la ruche")
    void lotManquant() {
        when(recoltes.findAll()).thenReturn(List.of(
                recolte(LocalDate.of(2026, 7, 1), null, false),
                recolte(LocalDate.of(2026, 7, 2), "   ", false),
                recolte(LocalDate.of(2026, 7, 3), "L-3", false)));

        PointControle p = point("tracabilite");

        // Un lot blanc n'est pas un lot : le compter comme renseigné laisserait
        // croire la chaîne complète.
        assertThat(p.statut()).isEqualTo("signale");
        assertThat(p.nombre()).isEqualTo(2);
    }

    @Test
    @DisplayName("aucune récolte du tout : la traçabilité est vérifiée, pas signalée")
    void aucuneRecolte() {
        // Rien à tracer n'est pas une chaîne rompue. C'est la seule lecture
        // honnête, et elle diffère de celle du registre de traitements — parce
        // qu'un traitement absent est une question, une récolte absente non.
        assertThat(point("tracabilite").statut()).isEqualTo("verifie");
    }
}
