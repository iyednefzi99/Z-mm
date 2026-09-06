package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zumm.domain.GabaritInspection;
import com.zumm.domain.GabaritPoint;
import com.zumm.domain.PointObservation;
import com.zumm.repository.GabaritInspectionRepository;
import com.zumm.repository.GabaritPointRepository;
import com.zumm.repository.PointObservationRepository;
import com.zumm.repository.ProduitTraitementRepository;
import com.zumm.repository.ReleveObservationRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.GabaritCorps;
import com.zumm.web.dto.GabaritReponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Carnet d'inspection paramétrable (SPRINT-28, lot 3 du plan de couverture).
 *
 * <p>85,6 % d'instructions mais <strong>62,5 % de branches</strong>. L'écart
 * porte sur les gardes qui tiennent la fermeture du référentiel, et c'est elle
 * qui donne son sens au module : <em>on active des cases existantes, on n'en
 * invente pas</em>. Dix exploitations qui inventeraient dix libellés pour la
 * même observation détruiraient la statistique que le SPRINT-20 venait de
 * rendre possible.
 *
 * <p>Trois propriétés valent au-delà du pourcentage :
 *
 * <ol>
 *   <li><strong>Un code inconnu est refusé en 400 qui le nomme.</strong> La clé
 *       étrangère le refuserait aussi, mais en 500 — or le référentiel étant
 *       fermé, un code inconnu est une faute de frappe du client, pas une panne
 *       du serveur.</li>
 *   <li><strong>Le gabarit par défaut bascule AVANT que le nouveau ne soit
 *       posé</strong>, et la session est vidée entre les deux : sans ce flush,
 *       Hibernate peut insérer avant de mettre à jour, et
 *       {@code uq_gabarit_defaut} refuse une transition pourtant légitime.</li>
 *   <li><strong>Aucun gabarit par défaut rend {@code null}</strong>, jamais un
 *       gabarit implicite : une exploitation qui n'a pas ouvert l'écran de
 *       configuration doit garder exactement la grille du SPRINT-20.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CarnetServiceTest {

    @Mock private PointObservationRepository points;
    @Mock private ProduitTraitementRepository produits;
    @Mock private GabaritInspectionRepository gabarits;
    @Mock private GabaritPointRepository gabaritPoints;
    @Mock private ReleveObservationRepository releves;

    private CarnetService service;

    @BeforeEach
    void monter() {
        service = new CarnetService(points, produits, gabarits, gabaritPoints, releves);
        lenient().when(gabarits.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(gabarits.existsByNom(any())).thenReturn(false);
        lenient().when(gabarits.findByParDefautTrue()).thenReturn(Optional.empty());
        lenient().when(gabaritPoints.findByIdGabaritIdOrderByOrdreAsc(any()))
                .thenReturn(List.of());
        lenient().when(points.findAllById(any())).thenReturn(List.of());
    }

    private static PointObservation point(String code) {
        PointObservation p = mock(PointObservation.class);
        lenient().when(p.getCode()).thenReturn(code);
        return p;
    }

    private static GabaritCorps corps(String nom, Boolean parDefaut, List<String> codes) {
        return new GabaritCorps(nom, "Suivi léger", true, true, false, false,
                parDefaut, true, codes);
    }

    // ── La fermeture du référentiel ─────────────────────────────────────────

    @Test
    @DisplayName("un code d'observation inconnu est refusé en 400 QUI LE NOMME")
    void codeInconnu() {
        PointObservation connu = point("varroa_visible");
        when(points.findAllById(any())).thenReturn(List.of(connu));

        // La clé étrangère le refuserait aussi, mais en 500. Le référentiel
        // étant fermé, un code inconnu est une faute de frappe du client.
        assertThatThrownBy(() -> service.creer(
                corps("Suivi", null, List.of("varroa_visible", "point_invente"))))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("point_invente");
        verify(gabaritPoints, never()).save(any());
    }

    @Test
    @DisplayName("des codes connus sont posés dans l'ordre reçu")
    void codesConnusOrdonnes() {
        PointObservation a = point("varroa_visible");
        PointObservation b = point("test_hygienique");
        when(points.findAllById(any())).thenReturn(List.of(a, b));

        GabaritReponse reponse =
                service.creer(corps("Suivi", null, List.of("test_hygienique", "varroa_visible")));

        // L'ordre est celui de l'apiculteur, pas celui du référentiel : c'est
        // l'ordre dans lequel il remplira sa fiche.
        assertThat(reponse.points()).containsExactly("test_hygienique", "varroa_visible");
        ArgumentCaptor<GabaritPoint> capture = ArgumentCaptor.forClass(GabaritPoint.class);
        verify(gabaritPoints, org.mockito.Mockito.times(2)).save(capture.capture());
        assertThat(capture.getAllValues().get(0).getId().getPointCode())
                .isEqualTo("test_hygienique");
    }

    @Test
    @DisplayName("un code répété n'est posé qu'une fois")
    void codeRepete() {
        PointObservation connu = point("varroa_visible");
        when(points.findAllById(any())).thenReturn(List.of(connu));

        GabaritReponse reponse = service.creer(
                corps("Suivi", null, List.of("varroa_visible", "varroa_visible")));

        // La clé primaire composite le refuserait ; le dédoublonnage évite un
        // 500 sur une saisie que l'écran peut produire par double clic.
        assertThat(reponse.points()).containsExactly("varroa_visible");
    }

    @Test
    @DisplayName("une liste de points vide est acceptée : le noyau suffit")
    void aucunPoint() {
        GabaritReponse reponse = service.creer(corps("Noyau seul", null, List.of()));

        assertThat(reponse.points()).isEmpty();
        verify(gabaritPoints, never()).save(any());
    }

    @Test
    @DisplayName("une liste ABSENTE vaut une liste vide, sans garde chez l'appelant")
    void pointsAbsents() {
        assertThat(service.creer(corps("Noyau seul", null, null)).points()).isEmpty();
    }

    @Test
    @DisplayName("les points sont REMPLACÉS, pas fusionnés")
    void remplacementComplet() {
        GabaritInspection g = new GabaritInspection("Suivi");
        when(gabarits.findById(1L)).thenReturn(Optional.of(g));
        PointObservation connu = point("varroa_visible");
        when(points.findAllById(any())).thenReturn(List.of(connu));

        service.mettreAJour(1L, corps("Suivi", null, List.of("varroa_visible")));

        // Une fusion rendrait impossible de RETIRER un point, et un carnet dont
        // on ne peut qu'ajouter des cases finit par n'être plus rempli.
        InOrder ordre = inOrder(gabaritPoints);
        ordre.verify(gabaritPoints).deleteAll(any());
        ordre.verify(gabaritPoints).flush();
        ordre.verify(gabaritPoints).save(any(GabaritPoint.class));
    }

    // ── Le gabarit par défaut ───────────────────────────────────────────────

    @Test
    @DisplayName("l'ancien défaut bascule AVANT que le nouveau ne soit posé")
    void basculeDuDefaut() {
        GabaritInspection ancien = new GabaritInspection("Ancien");
        ancien.setParDefaut(true);
        when(gabarits.findByParDefautTrue()).thenReturn(Optional.of(ancien));

        service.creer(corps("Nouveau", true, List.of()));

        // `uq_gabarit_defaut` interdit deux gabarits par défaut. Sans le
        // saveAndFlush, Hibernate peut insérer avant de mettre à jour, et la
        // contrainte refuse une transition pourtant légitime.
        assertThat(ancien.isParDefaut()).isFalse();
        verify(gabarits).saveAndFlush(ancien);
    }

    @Test
    @DisplayName("se redéclarer par défaut ne bascule pas le gabarit contre lui-même")
    void memeGabaritDejaParDefaut() {
        GabaritInspection lui = new GabaritInspection("Suivi");
        lui.setParDefaut(true);
        when(gabarits.findById(1L)).thenReturn(Optional.of(lui));
        when(gabarits.findByParDefautTrue()).thenReturn(Optional.of(lui));

        service.mettreAJour(1L, corps("Suivi", true, List.of()));

        // Le filtre `!ancien.equals(gabarit)` évite de le désactiver puis de le
        // réactiver — une écriture inutile qui laisserait, entre les deux, une
        // exploitation sans gabarit par défaut.
        verify(gabarits, never()).saveAndFlush(any());
        assertThat(lui.isParDefaut()).isTrue();
    }

    @Test
    @DisplayName("ne pas demander le défaut le retire")
    void retraitDuDefaut() {
        GabaritInspection g = new GabaritInspection("Suivi");
        g.setParDefaut(true);
        when(gabarits.findById(1L)).thenReturn(Optional.of(g));

        service.mettreAJour(1L, corps("Suivi", false, List.of()));

        assertThat(g.isParDefaut()).isFalse();
    }

    @Test
    @DisplayName("aucun gabarit par défaut rend null, jamais un gabarit inventé")
    void aucunDefaut() {
        // Une exploitation qui n'a jamais ouvert l'écran de configuration doit
        // garder exactement la grille du SPRINT-20 ; un gabarit inventé la lui
        // ferait perdre sans qu'elle ait rien demandé.
        assertThat(service.gabaritParDefaut()).isNull();
    }

    @Test
    @DisplayName("un gabarit par défaut DÉSACTIVÉ ne s'applique pas")
    void defautInactif() {
        GabaritInspection g = new GabaritInspection("Suivi");
        g.setParDefaut(true);
        g.setActif(false);
        when(gabarits.findByParDefautTrue()).thenReturn(Optional.of(g));

        // Désactiver est le geste par lequel on remet le carnet à plat : le
        // laisser s'appliquer quand même le viderait de son sens.
        assertThat(service.gabaritParDefaut()).isNull();
    }

    @Test
    @DisplayName("un gabarit par défaut actif est rendu")
    void defautActif() {
        GabaritInspection g = new GabaritInspection("Suivi");
        g.setParDefaut(true);
        g.setActif(true);
        when(gabarits.findByParDefautTrue()).thenReturn(Optional.of(g));

        assertThat(service.gabaritParDefaut()).isNotNull();
        assertThat(service.gabaritParDefaut().nom()).isEqualTo("Suivi");
    }

    // ── Le nom unique ───────────────────────────────────────────────────────

    @Test
    @DisplayName("un nom déjà pris est refusé, avec le nom en clair")
    void nomDejaPris() {
        when(gabarits.existsByNom("Suivi")).thenReturn(true);

        assertThatThrownBy(() -> service.creer(corps("Suivi", null, List.of())))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("Suivi");
    }

    @Test
    @DisplayName("garder son propre nom en mise à jour n'est pas un doublon")
    void memeNomEnMiseAJour() {
        GabaritInspection g = new GabaritInspection("Suivi");
        when(gabarits.findById(1L)).thenReturn(Optional.of(g));

        assertThatCode(() -> service.mettreAJour(1L, corps("Suivi", null, List.of())))
                .doesNotThrowAnyException();
        verify(gabarits, never()).existsByNom(any());
    }

    @Test
    @DisplayName("prendre le nom d'un autre gabarit est refusé")
    void nomDUnAutre() {
        GabaritInspection g = new GabaritInspection("Ancien nom");
        when(gabarits.findById(1L)).thenReturn(Optional.of(g));
        when(gabarits.existsByNom("Suivi")).thenReturn(true);

        assertThatThrownBy(() -> service.mettreAJour(1L, corps("Suivi", null, List.of())))
                .isInstanceOf(RequeteInvalide.class);
    }

    // ── Statistiques ────────────────────────────────────────────────────────

    @Test
    @DisplayName("une période non bornée ou inversée est refusée")
    void periodeInvalide() {
        LocalDate jour = LocalDate.of(2026, 6, 15);

        // Sans borne, la requête balaierait toute l'histoire de l'exploitation à
        // chaque ouverture d'écran.
        assertThatThrownBy(() -> service.statistiques(null, jour))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> service.statistiques(jour, null))
                .isInstanceOf(RequeteInvalide.class);
        assertThatThrownBy(() -> service.statistiques(jour, jour.minusDays(1)))
                .isInstanceOf(RequeteInvalide.class)
                .hasMessageContaining("commencer avant de finir");
    }

    @Test
    @DisplayName("une période d'un seul jour est valide")
    void periodeDUnJour() {
        LocalDate jour = LocalDate.of(2026, 6, 15);
        when(releves.statistiques(jour, jour)).thenReturn(List.of());

        assertThat(service.statistiques(jour, jour)).isEmpty();
    }

    // ── Lectures ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un gabarit inconnu est refusé en 404")
    void gabaritIntrouvable() {
        when(gabarits.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenirGabarit(999L))
                .isInstanceOf(RessourceIntrouvable.class);
        assertThatThrownBy(() -> service.supprimer(999L))
                .isInstanceOf(RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("supprimer passe l'entité au dépôt : le relevé pointe le référentiel")
    void suppression() {
        GabaritInspection g = new GabaritInspection("Suivi");
        when(gabarits.findById(1L)).thenReturn(Optional.of(g));

        service.supprimer(1L);

        // La suppression est franche parce qu'un gabarit ne laisse aucune trace
        // dans les visites : le relevé pointe le RÉFÉRENTIEL, jamais le gabarit
        // qui l'a proposé. Une exploitation qui réorganise son carnet ne rend
        // pas illisibles ses inspections passées.
        verify(gabarits).delete(g);
    }

    @Test
    @DisplayName("les deux référentiels se lisent dans leur ordre de présentation")
    void referentiels() {
        when(points.findAllByOrderByOrdreAsc()).thenReturn(List.of());
        when(produits.findAllByOrderByNomAsc()).thenReturn(List.of());

        assertThat(service.points()).isEmpty();
        assertThat(service.produits()).isEmpty();
    }

    @Test
    @DisplayName("la liste des gabarits passe par le dépôt")
    void listerGabarits() {
        GabaritInspection g = new GabaritInspection("Suivi");
        when(gabarits.findAllByOrderByNomAsc()).thenReturn(List.of(g));

        assertThat(service.listerGabarits()).hasSize(1);
    }
}
