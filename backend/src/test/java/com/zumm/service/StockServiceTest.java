package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Consommable;
import com.zumm.repository.ConsommableRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.ConsommableCorps;
import com.zumm.web.dto.ConsommableReponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Stock de consommables à seuils (SPRINT-27, lot 3 du plan de couverture).
 *
 * <p>61,4 % d'instructions. La décision qui porte toute la classe est dans sa
 * javadoc, et elle explique pourquoi l'API n'a pas la forme évidente :
 *
 * <blockquote>Le MOUVEMENT plutôt que la valeur absolue. Deux personnes qui
 * prélèvent du candi le même jour ne s'écrasent pas l'une l'autre — alors qu'une
 * saisie « il reste 12 kg » écrase, <strong>sans que personne ne le
 * voie</strong>.</blockquote>
 *
 * <p>La saisie du total reste possible par la modification, pour corriger après
 * un inventaire réel : les deux gestes coexistent parce qu'ils ne disent pas la
 * même chose.
 */
@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock private ConsommableRepository consommables;

    private StockService service;

    @BeforeEach
    void monter() {
        service = new StockService(consommables);
        lenient().when(consommables.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** Un consommable réel : ses accesseurs comptent, un mock nu ne les tiendrait pas. */
    private static Consommable consommable(String quantite) {
        Consommable c = new Consommable("Candi", "candi", "kg");
        c.setQuantite(new BigDecimal(quantite));
        c.setSeuilAlerte(new BigDecimal("5"));
        return c;
    }

    private static ConsommableCorps corps(String quantite, String seuil) {
        return new ConsommableCorps("  Candi  ", "candi", new BigDecimal(quantite), "kg",
                new BigDecimal(seuil), null);
    }

    // ── Le mouvement ────────────────────────────────────────────────────────

    @Test
    @DisplayName("un retrait diminue le stock sans poser de total")
    void retrait() {
        Consommable c = consommable("12.5");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        ConsommableReponse reponse = service.mouvementer(1L, new BigDecimal("-2.5"));

        // Deux personnes qui prélèvent le même jour ne s'écrasent pas : chacune
        // retire ce qu'elle a pris.
        assertThat(reponse.quantite()).isEqualByComparingTo("10.0");
        assertThat(c.getQuantite()).isEqualByComparingTo("10.0");
    }

    @Test
    @DisplayName("un apport augmente le stock")
    void apport() {
        Consommable c = consommable("12.5");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        assertThat(service.mouvementer(1L, new BigDecimal("7.5")).quantite())
                .isEqualByComparingTo("20.0");
    }

    @Test
    @DisplayName("un stock ne descend pas sous zéro, et le refus dit ce qui reste")
    void stockInsuffisant() {
        Consommable c = consommable("3");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        // La base le refuserait, mais le message serait celui de PostgreSQL. Le
        // refus est ici, AVEC la quantité disponible — c'est elle qui permet de
        // corriger la saisie sans rouvrir l'écran.
        assertThatThrownBy(() -> service.mouvementer(1L, new BigDecimal("-5")))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("il reste 3 kg");
        assertThat(c.getQuantite()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("descendre exactement à zéro est permis : un stock épuisé reste un stock")
    void jusquAZero() {
        Consommable c = consommable("3");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        assertThat(service.mouvementer(1L, new BigDecimal("-3")).quantite())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("un mouvement nul est accepté sans rien changer")
    void mouvementNul() {
        Consommable c = consommable("12.5");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        assertThat(service.mouvementer(1L, BigDecimal.ZERO).quantite())
                .isEqualByComparingTo("12.5");
    }

    // ── Le seuil ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le passage sous le seuil se lit sur la réponse")
    void sousSeuil() {
        Consommable c = consommable("12");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        assertThat(service.mouvementer(1L, new BigDecimal("-1")).sousSeuil()).isFalse();
        assertThat(service.mouvementer(1L, new BigDecimal("-6")).sousSeuil()).isTrue();
    }

    // ── Saisie ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le libellé est débarrassé de ses espaces de bord")
    void libelleNettoye() {
        assertThat(service.creer(corps("10", "5")).libelle()).isEqualTo("Candi");
    }

    @Test
    @DisplayName("la modification pose un TOTAL : c'est le geste d'après inventaire")
    void modificationPoseLeTotal() {
        Consommable c = consommable("12.5");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        // Les deux gestes coexistent parce qu'ils ne disent pas la même chose :
        // « j'ai pris deux kilos » et « j'ai compté, il en reste huit ».
        ConsommableReponse reponse = service.mettreAJour(1L, corps("8", "5"));

        assertThat(reponse.quantite()).isEqualByComparingTo("8");
        assertThat(reponse.libelle()).isEqualTo("Candi");
    }

    @Test
    @DisplayName("la liste suit l'ordre du dépôt : catégorie puis libellé")
    void liste() {
        when(consommables.findAllByOrderByCategorieAscLibelleAsc())
                .thenReturn(List.of(consommable("10")));

        assertThat(service.lister()).hasSize(1);
    }

    @Test
    @DisplayName("un consommable inconnu est refusé en 404")
    void consommableIntrouvable() {
        when(consommables.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mouvementer(999L, BigDecimal.ONE))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.mettreAJour(999L, corps("8", "5")))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt")
    void suppression() {
        Consommable c = consommable("10");
        when(consommables.findById(1L)).thenReturn(Optional.of(c));

        service.supprimer(1L);

        verify(consommables).delete(c);
    }
}
