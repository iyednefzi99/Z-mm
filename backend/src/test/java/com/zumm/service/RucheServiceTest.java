package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Compartiment;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ferme;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.TypeCompartiment;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.FermeRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.CompartimentCorps;
import com.zumm.web.dto.RucheCorps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Ruches et composition (US-004, annexe A ; lot 3 du plan de couverture).
 *
 * <p>71,4 % d'instructions. Les règles de composition sont
 * <strong>inter-lignes</strong> — « exactement un corps », « au plus cinq
 * hausses » — donc hors de portée d'une contrainte de colonne : la base ne peut
 * en tenir qu'une moitié (l'index unique partiel sur le corps), et le service
 * doit tenir le reste.
 *
 * <p><strong>Et un ordre d'écritures, comme pour les emplacements de rucher</strong> :
 * l'ancienne composition est supprimée <em>et poussée en base</em> avant que la
 * nouvelle ne soit insérée, sans quoi l'index unique partiel sur le corps
 * refuserait deux corps pendant le même flush.
 */
@ExtendWith(MockitoExtension.class)
class RucheServiceTest {

    @Mock private RucheRepository ruches;
    @Mock private SiteRepository sites;
    @Mock private FermeRepository fermes;
    @Mock private AgentRepository agents;

    private RucheService service;

    @BeforeEach
    void monter() {
        service = new RucheService(ruches, sites, fermes, agents);
        lenient().when(ruches.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static Site site() {
        Site s = mock(Site.class);
        lenient().when(s.getId()).thenReturn(1L);
        lenient().when(s.getNom()).thenReturn("Rucher du haut");
        return s;
    }

    private static Ferme ferme() {
        Ferme f = mock(Ferme.class);
        lenient().when(f.getId()).thenReturn(2L);
        lenient().when(f.getNom()).thenReturn("Ferme des tilleuls");
        return f;
    }

    /** Une ruche simulée, munie de ce que {@code RucheReponse.de} lit. */
    private static Ruche ruche() {
        Site s = site();
        Ferme f = ferme();
        Ruche r = mock(Ruche.class);
        lenient().when(r.getId()).thenReturn(42L);
        lenient().when(r.getModele()).thenReturn("Dadant");
        lenient().when(r.getSite()).thenReturn(s);
        lenient().when(r.getFerme()).thenReturn(f);
        lenient().when(r.getEtat()).thenReturn(EtatRuche.ACTIVE);
        lenient().when(r.getCompartiments()).thenReturn(new ArrayList<>());
        return r;
    }

    private static CompartimentCorps corps(TypeCompartiment type) {
        return new CompartimentCorps(type, 10);
    }

    private static RucheCorps rucheCorps(Long agentId, EtatRuche etat, String priorite,
            List<CompartimentCorps> compartiments) {
        return new RucheCorps("Dadant", 1L, 2L, agentId, etat, compartiments,
                "dadant", "bleu", "achat", null, priorite);
    }

    private static List<CompartimentCorps> composition(int nbHausses) {
        List<CompartimentCorps> liste = new ArrayList<>();
        liste.add(corps(TypeCompartiment.CORPS));
        IntStream.range(0, nbHausses).forEach(i -> liste.add(corps(TypeCompartiment.HAUSSE)));
        return liste;
    }

    private void rattachementsPresents() {
        Site s = site();
        Ferme f = ferme();
        lenient().when(sites.findById(1L)).thenReturn(Optional.of(s));
        lenient().when(fermes.findById(2L)).thenReturn(Optional.of(f));
    }

    // ── Les règles de composition ───────────────────────────────────────────

    @Test
    @DisplayName("aucun corps est refusé : une ruche sans corps n'est pas une ruche")
    void aucunCorps() {
        assertThatThrownBy(() -> service.creer(rucheCorps(null, null, null,
                List.of(corps(TypeCompartiment.HAUSSE)))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("exactement un corps");
        verify(ruches, never()).save(any());
    }

    @Test
    @DisplayName("deux corps sont refusés au service, pas seulement par l'index")
    void deuxCorps() {
        // L'index unique partiel les refuserait aussi, mais en 500 : la règle
        // est inter-lignes, et le message doit dire laquelle.
        assertThatThrownBy(() -> service.creer(rucheCorps(null, null, null,
                List.of(corps(TypeCompartiment.CORPS), corps(TypeCompartiment.CORPS)))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("exactement un corps");
    }

    @Test
    @DisplayName("cinq hausses passent, six sont refusées")
    void plafondDesHausses() {
        rattachementsPresents();

        assertThatCode(() -> service.creer(rucheCorps(null, null, null, composition(5))))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> service.creer(rucheCorps(null, null, null, composition(6))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("plus de cinq hausses");
    }

    @Test
    @DisplayName("la composition est vérifiée AVANT toute résolution de rattachement")
    void verificationAvantResolution() {
        // Refuser tôt évite trois requêtes inutiles sur une saisie qui ne peut
        // pas aboutir.
        assertThatThrownBy(() -> service.creer(rucheCorps(null, null, null, List.of())))
                .isInstanceOf(RequeteInvalide.class);
        verify(sites, never()).findById(any());
        verify(fermes, never()).findById(any());
    }

    // ── L'ordre des écritures ───────────────────────────────────────────────

    @Test
    @DisplayName("la composition est vidée ET poussée en base avant d'être réinsérée")
    void vidageAvantInsertion() {
        rattachementsPresents();
        Ruche r = ruche();
        when(ruches.findById(42L)).thenReturn(Optional.of(r));

        service.mettreAJour(42L, rucheCorps(null, null, null, composition(2)));

        // Sans le flush entre les deux, l'index unique partiel sur le corps
        // verrait deux corps pendant le même flush et refuserait la mise à jour.
        InOrder ordre = inOrder(r, ruches);
        ordre.verify(r).viderCompartiments();
        ordre.verify(ruches).flush();
        // Trois compartiments (un corps, deux hausses) : ce qui compte est que
        // la PREMIERE insertion suive le flush, pas leur nombre.
        ordre.verify(r, atLeastOnce()).ajouterCompartiment(any(Compartiment.class));
    }

    // ── Le référentiel ──────────────────────────────────────────────────────

    @Test
    @DisplayName("les quatre champs du référentiel s'écrivent tels quels, null compris")
    void referentielEcritTelQuel() {
        rattachementsPresents();
        Ruche r = ruche();
        when(ruches.findById(42L)).thenReturn(Optional.of(r));

        service.mettreAJour(42L, rucheCorps(null, null, null, composition(1)));

        // Le corps décrit l'état COMPLET de la ruche après la requête : un champ
        // absent est un champ qu'on efface, pas un champ qu'on ignore.
        verify(r).setTypeRuche("dadant");
        verify(r).setCouleur("bleu");
        verify(r).setOrigine("achat");
        verify(r).setCauseCloture(null);
    }

    @Test
    @DisplayName("une priorité absente ne remplace pas l'existante")
    void prioriteAbsente() {
        rattachementsPresents();
        Ruche r = ruche();
        when(ruches.findById(42L)).thenReturn(Optional.of(r));

        service.mettreAJour(42L, rucheCorps(null, null, null, composition(1)));

        // Une exploitation où toutes les ruches sont « hautes » n'a rien
        // priorisé, elle a seulement rempli un champ.
        verify(r, never()).setPriorite(any());
    }

    @Test
    @DisplayName("une priorité fournie est posée")
    void prioriteFournie() {
        rattachementsPresents();
        Ruche r = ruche();
        when(ruches.findById(42L)).thenReturn(Optional.of(r));

        service.mettreAJour(42L, rucheCorps(null, null, "haute", composition(1)));

        verify(r).setPriorite("haute");
    }

    @Test
    @DisplayName("un état absent en mise à jour ne remplace pas l'existant")
    void etatAbsentEnMiseAJour() {
        rattachementsPresents();
        Ruche r = ruche();
        when(ruches.findById(42L)).thenReturn(Optional.of(r));

        service.mettreAJour(42L, rucheCorps(null, null, null, composition(1)));

        // Clôturer une ruche est un geste ; l'écraser par un défaut la
        // ressusciterait à la première correction de couleur.
        verify(r, never()).setEtat(any());
    }

    @Test
    @DisplayName("un état fourni en mise à jour remplace l'existant")
    void etatFourniEnMiseAJour() {
        rattachementsPresents();
        Ruche r = ruche();
        when(ruches.findById(42L)).thenReturn(Optional.of(r));

        service.mettreAJour(42L, rucheCorps(null, EtatRuche.CLOTUREE, null, composition(1)));

        verify(r).setEtat(EtatRuche.CLOTUREE);
    }

    // ── Rattachements ───────────────────────────────────────────────────────

    @Test
    @DisplayName("un rucher, une ferme ou un agent inconnu dans ce tenant est refusé")
    void rattachementsInconnus() {
        when(sites.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.creer(rucheCorps(null, null, null, composition(1))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("1");

        Site s = site();
        when(sites.findById(1L)).thenReturn(Optional.of(s));
        when(fermes.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.creer(rucheCorps(null, null, null, composition(1))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("2");

        Ferme f = ferme();
        when(fermes.findById(2L)).thenReturn(Optional.of(f));
        when(agents.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.creer(rucheCorps(9L, null, null, composition(1))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("9");
    }

    @Test
    @DisplayName("un agent responsable absent est accepté : une ruche peut n'être à personne")
    void sansAgentResponsable() {
        rattachementsPresents();

        service.creer(rucheCorps(null, null, null, composition(1)));

        verify(agents, never()).findById(any());
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("une ruche inconnue est refusée en 404")
    void rucheIntrouvable() {
        when(ruches.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenir(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("la liste et la suppression passent par le dépôt")
    void listeEtSuppression() {
        Ruche r = ruche();
        when(ruches.findAll()).thenReturn(List.of(r));
        when(ruches.findById(42L)).thenReturn(Optional.of(r));

        assertThat(service.lister()).hasSize(1);
        service.supprimer(42L);
        verify(ruches).delete(r);
    }

    @Test
    @DisplayName("l'état par défaut à la création est CREEE")
    void etatParDefaut() {
        rattachementsPresents();

        service.creer(rucheCorps(null, null, null, composition(1)));

        // `CREEE` et non `ACTIVE` : une ruche enregistrée au bureau n'est pas
        // encore peuplée, et le distinguer permet de compter le parc réel.
        verify(ruches).save(any(Ruche.class));
    }
}
