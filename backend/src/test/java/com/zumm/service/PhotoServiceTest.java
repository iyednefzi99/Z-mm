package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.Photo;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.domain.Site;
import com.zumm.domain.SuiviReine;
import com.zumm.domain.Traitement;
import com.zumm.domain.Visite;
import com.zumm.repository.PhotoRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.repository.SuiviReineRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.PhotoCibleCorps;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Photos rattachées (SPRINT-28, lot 3 du plan de couverture).
 *
 * <p>57,3 % d'instructions et 41,7 % de branches, pour une classe qui est
 * presque entièrement un {@code switch} sur les <strong>six cibles</strong> de
 * {@code Photo}. Un tel switch est le cas où la sous-couverture se voit le
 * moins et coûte le plus : cinq branches sur six peuvent marcher, et la sixième
 * échoue le jour où quelqu'un attache une photo à une ordonnance.
 *
 * <p>L'invariant du dépôt est que {@code Photo} a <strong>six cibles et
 * exactement une par ligne</strong> ({@code ck_photo_cible_unique}). Ces tests
 * exercent les six dans les deux sens — résolution du porteur et lecture — par
 * un test paramétré sur l'énumération elle-même : ajouter une septième cible
 * sans la câbler fera tomber le test, et non la production.
 */
@ExtendWith(MockitoExtension.class)
class PhotoServiceTest {

    @Mock private PhotoRepository photos;
    @Mock private VisiteRepository visites;
    @Mock private RucheRepository ruches;
    @Mock private SiteRepository sites;
    @Mock private SuiviReineRepository reines;
    @Mock private RecolteRepository recoltes;
    @Mock private TraitementRepository traitements;

    private PhotoService service;

    @BeforeEach
    void monter() {
        service = new PhotoService(photos, visites, ruches, sites, reines, recoltes, traitements);
        lenient().when(photos.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** Rend le porteur attendu par la cible, et la liste que le dépôt renverra. */
    private void porteurPresent(Photo.Cible cible, Long id) {
        switch (cible) {
            case VISITE -> {
                lenient().when(visites.findById(id))
                        .thenReturn(Optional.of(mock(Visite.class)));
                lenient().when(photos.findByVisiteIdOrderByIdAsc(id)).thenReturn(List.of());
            }
            case RUCHE -> {
                lenient().when(ruches.findById(id)).thenReturn(Optional.of(mock(Ruche.class)));
                lenient().when(photos.findByRucheIdOrderByIdAsc(id)).thenReturn(List.of());
            }
            case SITE -> {
                lenient().when(sites.findById(id)).thenReturn(Optional.of(mock(Site.class)));
                lenient().when(photos.findBySiteIdOrderByIdAsc(id)).thenReturn(List.of());
            }
            case REINE -> {
                lenient().when(reines.findById(id))
                        .thenReturn(Optional.of(mock(SuiviReine.class)));
                lenient().when(photos.findByReineIdOrderByIdAsc(id)).thenReturn(List.of());
            }
            case RECOLTE -> {
                lenient().when(recoltes.findById(id))
                        .thenReturn(Optional.of(mock(Recolte.class)));
                lenient().when(photos.findByRecolteIdOrderByIdAsc(id)).thenReturn(List.of());
            }
            case TRAITEMENT -> {
                lenient().when(traitements.findById(id))
                        .thenReturn(Optional.of(mock(Traitement.class)));
                lenient().when(photos.findByTraitementIdOrderByIdAsc(id)).thenReturn(List.of());
            }
        }
    }

    /** Aucun porteur : la cible demandée n'existe pas dans ce tenant. */
    private void porteurAbsent(Photo.Cible cible, Long id) {
        switch (cible) {
            case VISITE -> lenient().when(visites.findById(id)).thenReturn(Optional.empty());
            case RUCHE -> lenient().when(ruches.findById(id)).thenReturn(Optional.empty());
            case SITE -> lenient().when(sites.findById(id)).thenReturn(Optional.empty());
            case REINE -> lenient().when(reines.findById(id)).thenReturn(Optional.empty());
            case RECOLTE -> lenient().when(recoltes.findById(id)).thenReturn(Optional.empty());
            case TRAITEMENT ->
                    lenient().when(traitements.findById(id)).thenReturn(Optional.empty());
        }
    }

    // ── Les six cibles ──────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(Photo.Cible.class)
    @DisplayName("chacune des six cibles s'attache, et une seule colonne est remplie")
    void attacheSurChaqueCible(Photo.Cible cible) {
        porteurPresent(cible, 1L);

        service.attacher(new PhotoCibleCorps(cible, 1L, "https://demo.zumm.tn/p.jpg", "Cadre"));

        // `Photo.sur` remplit exactement une colonne : c'est ce que garantit
        // `ck_photo_cible_unique` en base, et le switch doit dire la même chose.
        verify(photos).save(any(Photo.class));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Photo.Cible.class)
    @DisplayName("chacune des six cibles se relit par son propre finder")
    void listeSurChaqueCible(Photo.Cible cible) {
        porteurPresent(cible, 1L);

        assertThat(service.lister(cible, 1L)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Photo.Cible.class)
    @DisplayName("une cible inexistante est refusée en 400 qui la nomme, jamais en 500")
    void cibleInexistante(Photo.Cible cible) {
        porteurAbsent(cible, 99L);

        // La clé étrangère composite la refuserait aussi, mais en 500 : on
        // préfère un 400 qui nomme l'objet manquant. Et la résolution passe par
        // les repositories, donc par la RLS — attacher une photo à la ruche d'un
        // AUTRE tenant échoue exactement là où une lecture échouerait.
        assertThatThrownBy(() -> service.attacher(
                new PhotoCibleCorps(cible, 99L, "https://demo.zumm.tn/p.jpg", null)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("99")
                .hasMessageContaining("dans ce tenant");
        verify(photos, never()).save(any());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Photo.Cible.class)
    @DisplayName("lister une cible inexistante échoue avant d'interroger les photos")
    void listeSurCibleInexistante(Photo.Cible cible) {
        porteurAbsent(cible, 99L);

        // Sans cette vérification, un identifiant inconnu rendrait une liste
        // vide — indiscernable d'une cible réelle sans photo.
        assertThatThrownBy(() -> service.lister(cible, 99L))
                .isInstanceOf(RequeteInvalide.class);
    }

    // ── Suppression ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("supprimer passe l'entité au dépôt")
    void suppression() {
        Photo photo = mock(Photo.class);
        when(photos.findById(5L)).thenReturn(Optional.of(photo));

        service.supprimer(5L);

        verify(photos).delete(photo);
    }

    @Test
    @DisplayName("une photo inconnue est refusée en 404")
    void photoIntrouvable() {
        when(photos.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        verify(photos, never()).delete(any());
    }

    // ── Ce qui est stocké ───────────────────────────────────────────────────

    @Test
    @DisplayName("la photo ne porte qu'une ADRESSE, jamais le binaire")
    void seulementUneAdresse() {
        porteurPresent(Photo.Cible.RUCHE, 1L);

        service.attacher(new PhotoCibleCorps(Photo.Cible.RUCHE, 1L,
                "https://demo.zumm.tn/cadre.jpg", "Cadre de couvain"));

        // Le dépôt n'a aucun stockage binaire, et c'est un invariant : encoder
        // une image en base64 dans un champ texte fabriquerait un stockage de
        // fichiers clandestin, invisible en revue et hors de toute politique de
        // rétention. Le corps n'accepte qu'une URL ou un chemin.
        org.mockito.ArgumentCaptor<Photo> capture =
                org.mockito.ArgumentCaptor.forClass(Photo.class);
        verify(photos).save(capture.capture());
        assertThat(capture.getValue().getUrl()).isEqualTo("https://demo.zumm.tn/cadre.jpg");
        assertThat(capture.getValue().getLegende()).isEqualTo("Cadre de couvain");
    }

    @Test
    @DisplayName("une légende absente ne bloque rien")
    void legendeFacultative() {
        porteurPresent(Photo.Cible.SITE, 1L);

        service.attacher(new PhotoCibleCorps(Photo.Cible.SITE, 1L, "/stockage/p.jpg", null));

        verify(photos).save(any(Photo.class));
    }
}
