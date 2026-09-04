package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ruche;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.CibleLot;
import com.zumm.web.dto.RapportLot;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests du socle des operations de lot (SPRINT-23, lot B).
 *
 * <p>Le contenu de chaque operation a ses propres tests ; ce qui se joue ici est
 * la SEMANTIQUE du lot, et c'est elle qui decide si le produit tient a
 * l'echelle :
 *
 * <ol>
 *   <li>un echec n'annule pas les autres — trente-sept traitements ont bien eu
 *       lieu, les refuser en base ne changerait rien au rucher ;
 *   <li>le rapport NOMME les echecs, sans quoi le rejeu porterait sur les
 *       quarante ruches au lieu des trois ;
 *   <li>designer une ruche et son rucher ne la traite qu'une fois ;
 *   <li>une cible vide est une erreur d'appel, pas un lot de zero ligne.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class OperationsLotServiceTest {

    @Mock
    private RucheRepository ruches;

    private Ruche ruche(long id, EtatRuche etat) {
        Ruche ruche = mock(Ruche.class);
        org.mockito.Mockito.lenient().when(ruche.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(ruche.getEtat()).thenReturn(etat);
        return ruche;
    }

    private OperationsLotService service() {
        return new OperationsLotService(ruches);
    }

    @Test
    @DisplayName("un refus n'annule pas les reussites, et le rapport le dit")
    void echecPartiel() {
        // Les doublures se construisent AVANT le `when` : les imbriquer produirait
        // un stubbing inacheve, Mockito n'admettant pas qu'on stube pendant qu'on
        // stube.
        Ruche une = ruche(1L, EtatRuche.ACTIVE);
        Ruche deux = ruche(2L, EtatRuche.ACTIVE);
        Ruche trois = ruche(3L, EtatRuche.ACTIVE);
        when(ruches.findById(1L)).thenReturn(Optional.of(une));
        when(ruches.findById(2L)).thenReturn(Optional.of(deux));
        when(ruches.findById(3L)).thenReturn(Optional.of(trois));

        RapportLot rapport = service().executer(
                new CibleLot(List.of(1L, 2L, 3L), null),
                ruche -> {
                    if (ruche.getId() == 2L) {
                        throw new RequeteInvalide("Ruche sous carence jusqu'au 2026-08-14.");
                    }
                    return ruche.getId() * 10;
                });

        assertThat(rapport.demandees()).isEqualTo(3);
        assertThat(rapport.reussites()).containsExactly(10L, 30L);
        assertThat(rapport.echecs()).hasSize(1);
        // Le motif est celui du service metier : le remplacer par un message
        // generique ferait perdre la seule information exploitable.
        assertThat(rapport.echecs().get(0).rucheId()).isEqualTo(2L);
        assertThat(rapport.echecs().get(0).motif()).contains("2026-08-14");
        assertThat(rapport.partiel()).isTrue();
    }

    @Test
    @DisplayName("designer une ruche ET son rucher ne la traite qu'une fois")
    void deduplication() {
        Ruche premiere = ruche(1L, EtatRuche.ACTIVE);
        Ruche seconde = ruche(2L, EtatRuche.ACTIVE);
        when(ruches.findById(1L)).thenReturn(Optional.of(premiere));
        when(ruches.findBySite_IdOrderByIdAsc(7L)).thenReturn(List.of(premiere, seconde));

        RapportLot rapport = service().executer(
                new CibleLot(List.of(1L), 7L), Ruche::getId);

        // Trente ruches scannees puis « tout le rucher » doivent donner quarante
        // lignes, jamais soixante-dix.
        assertThat(rapport.demandees()).isEqualTo(2);
        assertThat(rapport.reussites()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("« tout le rucher » ecarte les ruches cloturees, en silence")
    void clotureesEcartees() {
        Ruche active = ruche(1L, EtatRuche.ACTIVE);
        Ruche cloturee = ruche(2L, EtatRuche.CLOTUREE);
        Ruche peuplee = ruche(3L, EtatRuche.PEUPLEE);
        when(ruches.findBySite_IdOrderByIdAsc(7L))
                .thenReturn(List.of(active, cloturee, peuplee));

        RapportLot rapport = service().executer(new CibleLot(null, 7L), Ruche::getId);

        // Faire echouer des lignes pour des colonies mortes il y a six mois
        // transformerait chaque rapport en liste de bruit.
        assertThat(rapport.demandees()).isEqualTo(2);
        assertThat(rapport.reussites()).containsExactly(1L, 3L);
        assertThat(rapport.echecs()).isEmpty();
    }

    @Test
    @DisplayName("une ruche cloturee NOMMEMENT designee, elle, produit un echec visible")
    void clotureeDesigneeExplicitement() {
        Ruche cloturee = ruche(2L, EtatRuche.CLOTUREE);
        when(ruches.findById(2L)).thenReturn(Optional.of(cloturee));

        RapportLot rapport = service().executer(
                new CibleLot(List.of(2L), null),
                ruche -> {
                    throw new RequeteInvalide("Ruche cloturee.");
                });

        // C'est une erreur de l'appelant : la taire lui laisserait croire que
        // l'acte a eu lieu.
        assertThat(rapport.echecs()).hasSize(1);
        assertThat(rapport.reussites()).isEmpty();
    }

    @Test
    @DisplayName("une cible vide est refusee, pas traitee comme un lot de zero ligne")
    void cibleVide() {
        assertThatThrownBy(() -> service().executer(new CibleLot(null, null), Ruche::getId))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("au moins une ruche");

        assertThatThrownBy(() -> service().executer(null, Ruche::getId))
                .isInstanceOf(RequeteInvalide.class);
    }

    @Test
    @DisplayName("un rucher sans ruche vivante est refuse : l'operation n'aurait rien fait")
    void rucherSansRucheVivante() {
        Ruche cloturee = ruche(1L, EtatRuche.CLOTUREE);
        when(ruches.findBySite_IdOrderByIdAsc(7L)).thenReturn(List.of(cloturee));

        assertThatThrownBy(() -> service().executer(new CibleLot(null, 7L), Ruche::getId))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Aucune ruche vivante");
    }

    @Test
    @DisplayName("une ruche inconnue arrete l'operation avant tout effet")
    void rucheInconnue() {
        when(ruches.findById(99L)).thenReturn(Optional.empty());

        // Refuse AVANT d'executer quoi que ce soit : un identifiant faux est une
        // erreur de saisie, et la moitie d'un lot deja applique serait pire qu'un
        // refus net.
        assertThatThrownBy(() -> service().executer(new CibleLot(List.of(99L), null), Ruche::getId))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("99");
    }
}
