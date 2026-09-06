package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Ferme;
import com.zumm.domain.Fermier;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.FermierRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.FermeCorps;
import com.zumm.web.dto.FermeReponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Exploitations (US-002, lot 3 du plan de couverture).
 *
 * <p><strong>40,6 % d'instructions</strong> — le plus mauvais chiffre du paquet,
 * et de loin, sur une classe pourtant triviale. La cause est instructive : les
 * tests d'intégration créent des fermes par la route et vérifient qu'elle
 * répond, mais <em>aucun</em> n'exerce la lecture paginée ni la mise à jour, et
 * surtout aucun n'exerce le seul contrôle que porte la classe.
 *
 * <p>Ce contrôle est le rattachement au fermier, et sa raison est écrite dans la
 * javadoc du service : la clé composite {@code (fermier_id, tenant_id)} refuse
 * déjà le rattachement inter-tenant, mais elle le refuse en <strong>500</strong>.
 * Le service le refuse en <strong>400 qui nomme l'identifiant</strong> — un
 * fermier absent du tenant courant est une requête fausse, pas une panne.
 */
@ExtendWith(MockitoExtension.class)
class FermeServiceTest {

    @Mock private FermeRepository fermes;
    @Mock private FermierRepository fermiers;

    private FermeService service;

    @BeforeEach
    void monter() {
        service = new FermeService(fermes, fermiers);
        lenient().when(fermes.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Fermier fermier() {
        Fermier f = mock(Fermier.class);
        lenient().when(f.getId()).thenReturn(7L);
        lenient().when(f.getNom()).thenReturn("Nefzi");
        return f;
    }

    // ── Le seul contrôle de la classe ───────────────────────────────────────

    @Test
    @DisplayName("un fermier absent du tenant est refusé en 400 qui le nomme")
    void fermierInconnuALaCreation() {
        when(fermiers.findById(7L)).thenReturn(Optional.empty());

        // La clé composite (fermier_id, tenant_id) le refuserait aussi — mais en
        // 500, sur un message de PostgreSQL. Un fermier hors tenant est une
        // requête fausse, pas une panne.
        assertThatThrownBy(() -> service.creer(new FermeCorps("Rucher du Cap", 7L)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Fermier inconnu dans ce tenant : 7");
        verify(fermes, never()).save(any());
    }

    @Test
    @DisplayName("la mise à jour porte le MÊME contrôle que la création")
    void fermierInconnuALaMiseAJour() {
        Ferme ferme = new Ferme("Ancien", fermier());
        when(fermes.findById(1L)).thenReturn(Optional.of(ferme));
        when(fermiers.findById(7L)).thenReturn(Optional.empty());

        // Un contrôle posé à la création et oublié à la modification laisse
        // exactement le trou qu'il prétendait fermer : il suffit de créer puis
        // de modifier.
        assertThatThrownBy(() -> service.mettreAJour(1L, new FermeCorps("Rucher", 7L)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("7");
    }

    @Test
    @DisplayName("créer rattache la ferme au fermier résolu")
    void creation() {
        Fermier f = fermier();
        when(fermiers.findById(7L)).thenReturn(Optional.of(f));

        FermeReponse reponse = service.creer(new FermeCorps("Rucher du Cap", 7L));

        assertThat(reponse.nom()).isEqualTo("Rucher du Cap");
        assertThat(reponse.fermierId()).isEqualTo(7L);
        assertThat(reponse.fermierNom()).isEqualTo("Nefzi");
    }

    @Test
    @DisplayName("la mise à jour change le nom ET le rattachement")
    void miseAJour() {
        Fermier ancien = mock(Fermier.class);
        Fermier nouveau = fermier();
        Ferme ferme = new Ferme("Ancien", ancien);
        when(fermes.findById(1L)).thenReturn(Optional.of(ferme));
        when(fermiers.findById(7L)).thenReturn(Optional.of(nouveau));

        FermeReponse reponse = service.mettreAJour(1L, new FermeCorps("Nouveau", 7L));

        assertThat(reponse.nom()).isEqualTo("Nouveau");
        assertThat(reponse.fermierId()).isEqualTo(7L);
        assertThat(ferme.getFermier()).isSameAs(nouveau);
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("la liste complète mappe chaque ferme")
    void liste() {
        List<Ferme> deux = List.of(new Ferme("A", fermier()), new Ferme("B", fermier()));
        when(fermes.findAll()).thenReturn(deux);

        assertThat(service.lister()).extracting(FermeReponse::nom).containsExactly("A", "B");
    }

    @Test
    @DisplayName("la page porte son propre total, il n'est pas recompté")
    void listePaginee() {
        // Recompter derrière la Page ferait une seconde requête pour une valeur
        // que la première a déjà rendue.
        PageImpl<Ferme> page = new PageImpl<>(List.of(new Ferme("A", fermier())),
                PageRequest.of(0, 2), 9);
        when(fermes.findAll(PageRequest.of(0, 2))).thenReturn(page);

        var rendue = service.lister(PageRequest.of(0, 2));

        assertThat(rendue.getTotalElements()).isEqualTo(9);
        assertThat(rendue.getContent()).extracting(FermeReponse::nom).containsExactly("A");
    }

    @Test
    @DisplayName("obtenir mappe l'entité du tenant")
    void obtenir() {
        Ferme ferme = new Ferme("Rucher", fermier());
        when(fermes.findById(1L)).thenReturn(Optional.of(ferme));

        assertThat(service.obtenir(1L).nom()).isEqualTo("Rucher");
    }

    @Test
    @DisplayName("une ferme inconnue est refusée en 404 sur les trois chemins")
    void fermeIntrouvable() {
        when(fermes.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(999L)).isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.mettreAJour(999L, new FermeCorps("X", 7L)))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L)).isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer résout l'entité d'abord, ce qui donne le 404")
    void suppression() {
        Ferme ferme = new Ferme("Rucher", fermier());
        when(fermes.findById(1L)).thenReturn(Optional.of(ferme));

        // C'est la résolution préalable qui distingue « supprimée » de
        // « inexistante » : le test précédent vérifie qu'elle refuse en 404.
        service.supprimer(1L);

        verify(fermes).delete(ferme);
    }
}
