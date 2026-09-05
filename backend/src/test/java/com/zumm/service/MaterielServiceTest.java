package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Materiel;
import com.zumm.domain.Site;
import com.zumm.repository.MaterielRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MaterielCorps;
import com.zumm.web.dto.MaterielReponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Inventaire du matériel et plan d'entretien (SPRINT-27, lot 3 du plan de
 * couverture).
 *
 * <p>60,5 % d'instructions et 57,1 % de branches. Deux gestes y portent une
 * décision, et ni l'un ni l'autre n'était vérifié :
 *
 * <ul>
 *   <li><strong>Un entretien ne se date pas dans l'avenir.</strong> Sans ce
 *       refus, une faute de frappe repousserait l'échéance de plusieurs mois et
 *       le matériel disparaîtrait du plan de maintenance sans que personne ne le
 *       remarque — c'est un oubli qui ne se voit qu'à la panne.</li>
 *   <li><strong>Un entretien remet l'état à « bon », jamais à « neuf ».</strong>
 *       Le matériel révisé n'est pas du matériel neuf, et le prétendre ferait
 *       perdre la trace de son âge.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MaterielServiceTest {

    @Mock private MaterielRepository materiels;
    @Mock private SiteRepository sites;

    private MaterielService service;

    @BeforeEach
    void monter() {
        service = new MaterielService(materiels, sites);
        lenient().when(materiels.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static MaterielCorps corps(String libelle, int quantite, Long siteId, String etat,
            Integer periodicite, LocalDate derniereMaintenance) {
        return new MaterielCorps(libelle, "extracteur", quantite, siteId, etat, periodicite,
                derniereMaintenance, null);
    }

    // ── Création ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le libellé est débarrassé de ses espaces de bord")
    void libelleNettoye() {
        MaterielReponse reponse =
                service.creer(corps("  Extracteur 9 cadres  ", 2, null, null, null, null));

        assertThat(reponse.libelle()).isEqualTo("Extracteur 9 cadres");
        assertThat(reponse.quantite()).isEqualTo(2);
    }

    @Test
    @DisplayName("une quantité nulle devient un, pas zéro")
    void quantiteNulleDevientUn() {
        // Un matériel enregistré en zéro exemplaire n'existe pas : c'est une
        // saisie incomplète, et la refuser en 400 coûterait plus que de la
        // corriger vers le seul entier qui ait un sens.
        assertThat(service.creer(corps("Enfumoir", 0, null, null, null, null)).quantite())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("un rucher inconnu dans ce tenant est refusé à la saisie")
    void siteInconnu() {
        when(sites.findById(99L)).thenReturn(Optional.empty());

        // « dans ce tenant » : le rucher peut exister ailleurs, et c'est ce que
        // la RLS empêche de voir.
        assertThatThrownBy(() -> service.creer(corps("Extracteur", 1, 99L, null, null, null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("99");
        verify(materiels, never()).save(any());
    }

    @Test
    @DisplayName("un matériel peut n'être rattaché à aucun rucher")
    void sansRucher() {
        service.creer(corps("Maturateur", 1, null, null, null, null));

        verify(sites, never()).findById(any());
    }

    @Test
    @DisplayName("un rucher connu est rattaché")
    void avecRucher() {
        Site site = mock(Site.class);
        when(sites.findById(1L)).thenReturn(Optional.of(site));

        service.creer(corps("Extracteur", 1, 1L, null, null, null));

        verify(sites).findById(1L);
    }

    // ── Mise à jour ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("une quantité nulle en mise à jour ne remplace pas l'existante")
    void miseAJourSansQuantite() {
        Materiel existant = new Materiel("Extracteur", "extracteur", 3);
        when(materiels.findById(5L)).thenReturn(Optional.of(existant));

        MaterielReponse reponse =
                service.mettreAJour(5L, corps("Extracteur révisé", 0, null, "bon", 365, null));

        // Zéro veut dire « je ne touche pas à la quantité » : l'écrire écraserait
        // un inventaire par une saisie qui ne le concernait pas.
        assertThat(reponse.quantite()).isEqualTo(3);
        assertThat(reponse.libelle()).isEqualTo("Extracteur révisé");
    }

    @Test
    @DisplayName("un matériel inconnu est refusé en 404")
    void materielIntrouvable() {
        when(materiels.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe bien l'entité au dépôt")
    void suppression() {
        Materiel existant = new Materiel("Extracteur", "extracteur", 1);
        when(materiels.findById(5L)).thenReturn(Optional.of(existant));

        service.supprimer(5L);

        verify(materiels).delete(existant);
    }

    @Test
    @DisplayName("la liste suit l'ordre du dépôt : catégorie puis libellé")
    void liste() {
        Materiel un = new Materiel("Extracteur", "extracteur", 1);
        when(materiels.findAllByOrderByCategorieAscLibelleAsc()).thenReturn(List.of(un));

        assertThat(service.lister()).hasSize(1);
    }

    // ── Entretien ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("un entretien daté dans l'avenir est refusé")
    void entretienDansLAvenir() {
        Materiel existant = new Materiel("Extracteur", "extracteur", 1);
        when(materiels.findById(5L)).thenReturn(Optional.of(existant));

        // Une faute de frappe sur l'année repousserait l'échéance de plusieurs
        // mois, et le matériel sortirait du plan de maintenance sans bruit.
        assertThatThrownBy(() -> service.entretenir(5L, LocalDate.now().plusDays(1)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("avenir");
        assertThat(existant.getDerniereMaintenance()).isNull();
    }

    @Test
    @DisplayName("un entretien daté d'aujourd'hui est accepté")
    void entretienDuJour() {
        Materiel existant = new Materiel("Extracteur", "extracteur", 1);
        when(materiels.findById(5L)).thenReturn(Optional.of(existant));

        service.entretenir(5L, LocalDate.now());

        assertThat(existant.getDerniereMaintenance()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("sans date, l'entretien est daté du jour")
    void entretienSansDate() {
        Materiel existant = new Materiel("Extracteur", "extracteur", 1);
        when(materiels.findById(5L)).thenReturn(Optional.of(existant));

        service.entretenir(5L, null);

        assertThat(existant.getDerniereMaintenance()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("un entretien remet « à réviser » à « bon », jamais à « neuf »")
    void entretienRemetAEtatBon() {
        Materiel aReviser = new Materiel("Extracteur", "extracteur", 1);
        aReviser.setEtat("a_reviser");
        when(materiels.findById(5L)).thenReturn(Optional.of(aReviser));

        service.entretenir(5L, LocalDate.now().minusDays(1));

        // Le matériel révisé n'est pas du matériel neuf : le prétendre ferait
        // perdre la trace de son âge, et « neuf » cesserait de vouloir dire
        // quelque chose dans l'inventaire.
        assertThat(aReviser.getEtat()).isEqualTo("bon");
    }

    @Test
    @DisplayName("un entretien ne dégrade pas un état déjà meilleur, ni ne relève un hors service")
    void entretienNeTouchePasAuxAutresEtats() {
        Materiel neuf = new Materiel("Extracteur", "extracteur", 1);
        neuf.setEtat("neuf");
        Materiel horsService = new Materiel("Balance", "balance", 1);
        horsService.setEtat("hors_service");
        when(materiels.findById(5L)).thenReturn(Optional.of(neuf));
        when(materiels.findById(6L)).thenReturn(Optional.of(horsService));

        service.entretenir(5L, LocalDate.now());
        service.entretenir(6L, LocalDate.now());

        // Un entretien ne ressuscite pas un matériel hors service : c'est une
        // décision de mise au rebut, et elle se reprend à la main.
        assertThat(neuf.getEtat()).isEqualTo("neuf");
        assertThat(horsService.getEtat()).isEqualTo("hors_service");
    }
}
