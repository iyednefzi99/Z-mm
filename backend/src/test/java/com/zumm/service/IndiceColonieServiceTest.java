package com.zumm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zumm.domain.Agent;
import com.zumm.domain.ComptageVarroa;
import com.zumm.domain.EtatRuche;
import com.zumm.domain.EtatSante;
import com.zumm.domain.ObservationPathologie;
import com.zumm.domain.RaisonVisite;
import com.zumm.domain.RoleAgent;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.ComptageVarroaRepository;
import com.zumm.repository.ObservationPathologieRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.IndiceColonie;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Indices de colonie (SPRINT-22, lot 3 du plan de couverture).
 *
 * <p><strong>Le pire ratio de branches du dépôt hors moteur de règles</strong> :
 * 38,9 %, pour 44 branches manquantes, et aucun test. C'est le cas où la
 * sous-couverture coûte le plus cher — un indice se calcule à chaque lecture, ne
 * se stocke jamais, et une branche fausse ne casse rien : elle affiche un
 * nombre. L'apiculteur le lit, s'y fie, et rien ne le contredit.
 *
 * <p>Deux règles portent tout le reste, et ce sont elles que ces tests
 * verrouillent :
 *
 * <ol>
 *   <li><strong>Une composante n'est comptée que si elle a été OBSERVÉE.</strong>
 *       Sans observation, {@code composantes} vaut zéro et la santé est forcée à
 *       zéro — pas à 100. Une colonie non visitée n'est pas saine, elle est
 *       inconnue, et l'écran doit pouvoir le dire.</li>
 *   <li><strong>Le risque d'essaimage vaut 0 quand rien n'est observé</strong>,
 *       jamais une valeur moyenne. Dire « 50 » sur une colonie non visitée ferait
 *       déplacer l'apiculteur pour rien — ou pire, le rassurerait.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class IndiceColonieServiceTest {

    @Mock private RucheRepository ruches;
    @Mock private VisiteRepository visites;
    @Mock private ComptageVarroaRepository comptages;
    @Mock private ObservationPathologieRepository pathologies;

    private IndiceColonieService service;

    private static final LocalDate JOUR = LocalDate.of(2026, 9, 5);
    private static final Agent AGENT = new Agent("Amal", RoleAgent.APICULTEUR, null);

    @BeforeEach
    void monter() {
        service = new IndiceColonieService(ruches, visites, comptages, pathologies);
        lenient().when(pathologies.findByVisite_IdOrderByPathologieAsc(any()))
                .thenReturn(List.of());
        lenient().when(comptages.findFirstByRuche_IdOrderByDateComptageDescIdDesc(any()))
                .thenReturn(Optional.empty());
    }

    private static Ruche ruche() {
        return new Ruche("Dadant", null, null, EtatRuche.ACTIVE);
    }

    /** Une visite d'hier : toujours dans la fenêtre de fraîcheur. */
    private static Visite visite() {
        return new Visite(ruche(), AGENT, JOUR.minusDays(1), RaisonVisite.CONTROLE);
    }

    private IndiceColonie calculer(Visite derniere) {
        when(visites.findFirstByRuche_IdOrderByDateVisiteDescIdDesc(any()))
                .thenReturn(Optional.ofNullable(derniere));
        return service.calculer(ruche(), JOUR);
    }

    // ── Ce qui n'a pas été observé ──────────────────────────────────────────

    @Test
    @DisplayName("aucune visite : zéro composante, santé à zéro, et l'écran ne doit rien affirmer")
    void aucuneVisite() {
        IndiceColonie indice = calculer(null);

        // Zéro, et non 100 : l'ignorance ne se lit pas comme de la santé.
        assertThat(indice.composantes()).isZero();
        assertThat(indice.sante()).isZero();
        assertThat(indice.risqueEssaimage()).isZero();
        assertThat(indice.nonEvalue()).isTrue();
        assertThat(indice.derniereVisite()).isNull();
        assertThat(indice.motifs()).isEmpty();
    }

    @Test
    @DisplayName("une visite trop vieille est ignorée comme une absence de visite")
    void visiteHorsFraicheur() {
        Visite vieille = new Visite(ruche(), AGENT, JOUR.minusDays(46), RaisonVisite.CONTROLE);
        vieille.setEtatSante(EtatSante.MAUVAIS);

        IndiceColonie indice = calculer(vieille);

        // Quarante-six jours : au-delà de la fenêtre. Un « mauvais état » d'il y
        // a sept semaines ne dit rien de la colonie d'aujourd'hui, ni en bien
        // ni en mal.
        assertThat(indice.nonEvalue()).isTrue();
        assertThat(indice.sante()).isZero();
        assertThat(indice.motifs()).isEmpty();
    }

    @Test
    @DisplayName("exactement quarante-cinq jours : encore dans la fenêtre")
    void bordDeLaFenetre() {
        Visite limite = new Visite(ruche(), AGENT, JOUR.minusDays(45), RaisonVisite.CONTROLE);
        limite.setEtatSante(EtatSante.BON);

        IndiceColonie indice = calculer(limite);

        assertThat(indice.nonEvalue()).isFalse();
        assertThat(indice.sante()).isEqualTo(100);
    }

    @Test
    @DisplayName("une visite sans aucune observation reste non évaluée")
    void visiteVide() {
        IndiceColonie indice = calculer(visite());

        // La visite existe, mais rien n'y a été noté : compter la visite comme
        // une composante ferait passer « on est passé » pour « tout va bien ».
        assertThat(indice.composantes()).isZero();
        assertThat(indice.nonEvalue()).isTrue();
        assertThat(indice.derniereVisite()).isEqualTo(JOUR.minusDays(1));
    }

    // ── Santé ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("santé")
    class Sante {

        @Test
        @DisplayName("une observation favorable compte une composante sans rien retirer")
        void observationFavorable() {
            Visite v = visite();
            v.setEtatSante(EtatSante.BON);

            IndiceColonie indice = calculer(v);

            assertThat(indice.composantes()).isEqualTo(1);
            assertThat(indice.sante()).isEqualTo(100);
            assertThat(indice.motifs()).isEmpty();
        }

        @Test
        @DisplayName("chaque dégradation retire son poids et laisse un motif nommé")
        void degradationsMotivees() {
            Visite moyen = visite();
            moyen.setEtatSante(EtatSante.MOYEN);
            Visite mauvais = visite();
            mauvais.setEtatSante(EtatSante.MAUVAIS);

            // Un motif, et non un simple nombre : c'est ce qui rend l'indice
            // discutable par l'apiculteur au lieu d'être à croire sur parole.
            assertThat(calculer(moyen).sante()).isEqualTo(85);
            assertThat(calculer(moyen).motifs()).containsExactly("etat_sante_moyen");

            monter();
            assertThat(calculer(mauvais).sante()).isEqualTo(65);
            assertThat(calculer(mauvais).motifs()).containsExactly("etat_sante_mauvais");
        }

        @Test
        @DisplayName("pas de couvain operculé ET reine non vue : le couple qui inquiète")
        void couvainAbsentEtReineNonVue() {
            Visite v = visite();
            v.setCouvainOpercule(false);
            v.setReineVue(false);

            IndiceColonie indice = calculer(v);

            // Séparément chacun s'explique ; ensemble ils désignent une colonie
            // qui a perdu sa ponte. D'où le retrait supplémentaire.
            assertThat(indice.sante()).isEqualTo(100 - 20 - 15);
            assertThat(indice.motifs())
                    .containsExactly("pas_de_couvain_opercule", "reine_non_vue");
        }

        @Test
        @DisplayName("pas de couvain mais reine vue : le retrait supplémentaire ne s'applique pas")
        void couvainAbsentMaisReineVue() {
            Visite v = visite();
            v.setCouvainOpercule(false);
            v.setReineVue(true);

            IndiceColonie indice = calculer(v);

            assertThat(indice.sante()).isEqualTo(80);
            assertThat(indice.motifs()).containsExactly("pas_de_couvain_opercule");
        }

        @Test
        @DisplayName("couvain operculé présent : composante comptée, rien retiré")
        void couvainPresent() {
            Visite v = visite();
            v.setCouvainOpercule(true);

            IndiceColonie indice = calculer(v);

            assertThat(indice.composantes()).isEqualTo(1);
            assertThat(indice.sante()).isEqualTo(100);
        }

        @Test
        @DisplayName("les trois qualités de ponte ont chacune leur poids")
        void motifsDePonte() {
            for (String motif : List.of("lacunaire", "irregulier")) {
                monter();
                Visite v = visite();
                v.setMotifPonte(motif);
                assertThat(calculer(v).sante()).isEqualTo(90);
                assertThat(calculer(v).motifs()).containsExactly("ponte_" + motif);
            }

            monter();
            Visite absente = visite();
            absente.setMotifPonte("absent");
            assertThat(calculer(absente).sante()).isEqualTo(75);
            assertThat(calculer(absente).motifs()).containsExactly("ponte_absente");

            monter();
            Visite normale = visite();
            normale.setMotifPonte("compact");
            assertThat(calculer(normale).sante()).isEqualTo(100);
            assertThat(calculer(normale).composantes()).isEqualTo(1);
        }

        @Test
        @DisplayName("un cadre de miel ou moins est le début d'une famine, pas une variation")
        void reservesBasses() {
            Visite basse = visite();
            basse.setCadresMiel(1);
            Visite correcte = visite();
            correcte.setCadresMiel(2);

            assertThat(calculer(basse).sante()).isEqualTo(80);
            assertThat(calculer(basse).motifs()).containsExactly("reserves_basses");

            monter();
            assertThat(calculer(correcte).sante()).isEqualTo(100);
            assertThat(calculer(correcte).composantes()).isEqualTo(1);
        }

        @Test
        @DisplayName("une pathologie seulement SUSPECTÉE ne fait pas chuter l'indice")
        void pathologieSuspectee() {
            Visite v = visite();
            when(pathologies.findByVisite_IdOrderByPathologieAsc(any()))
                    .thenReturn(List.of(new ObservationPathologie(v, "nosema", "suspectee")));

            IndiceColonie indice = calculer(v);

            // Au rucher on constate un symptôme, on ne pose pas un diagnostic.
            // Compter le doute comme une maladie ferait chuter l'indice de toute
            // colonie qu'on a regardée de près.
            assertThat(indice.composantes()).isEqualTo(1);
            assertThat(indice.sante()).isEqualTo(100);
            assertThat(indice.motifs()).isEmpty();
        }

        @Test
        @DisplayName("une pathologie confirmée retire vingt points, deux en retirent quarante")
        void pathologiesConfirmees() {
            Visite v = visite();
            when(pathologies.findByVisite_IdOrderByPathologieAsc(any()))
                    .thenReturn(List.of(new ObservationPathologie(v, "loque", "confirmee")));

            assertThat(calculer(v).sante()).isEqualTo(80);
            assertThat(calculer(v).motifs()).containsExactly("pathologie_confirmee");
        }

        @Test
        @DisplayName("le retrait pour pathologies est plafonné à quarante points")
        void plafondDesPathologies() {
            Visite v = visite();
            when(pathologies.findByVisite_IdOrderByPathologieAsc(any())).thenReturn(List.of(
                    new ObservationPathologie(v, "loque", "confirmee"),
                    new ObservationPathologie(v, "nosema", "confirmee"),
                    new ObservationPathologie(v, "varroose", "confirmee")));

            // Trois pathologies ne retirent pas soixante points : au-delà du
            // plafond, l'indice cesserait de distinguer une colonie malade d'une
            // colonie perdue.
            assertThat(calculer(v).sante()).isEqualTo(60);
        }

        @Test
        @DisplayName("la santé ne descend jamais sous zéro")
        void planchezZero() {
            Visite v = visite();
            v.setEtatSante(EtatSante.MAUVAIS);
            v.setCouvainOpercule(false);
            v.setReineVue(false);
            v.setMotifPonte("absent");
            v.setCadresMiel(0);
            when(pathologies.findByVisite_IdOrderByPathologieAsc(any())).thenReturn(List.of(
                    new ObservationPathologie(v, "loque", "confirmee"),
                    new ObservationPathologie(v, "nosema", "confirmee")));

            IndiceColonie indice = calculer(v);

            // 100 − 35 − 20 − 15 − 25 − 20 − 40 = −55 : un indice négatif ne se
            // lit pas, et « 0 » dit déjà tout ce qu'il y a à dire.
            assertThat(indice.sante()).isZero();
            assertThat(indice.composantes()).isEqualTo(5);
            assertThat(indice.motifs()).hasSize(6);
        }
    }

    // ── Varroa ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("varroa")
    class Varroa {

        private ComptageVarroa comptage(String methode, int varroas, Integer echantillon,
                Integer jours, LocalDate date) {
            ComptageVarroa c = new ComptageVarroa(ruche(), AGENT, date, methode, varroas);
            c.setAbeillesEchantillon(echantillon);
            c.setJoursExposition(jours);
            return c;
        }

        private IndiceColonie avecComptage(ComptageVarroa c) {
            when(comptages.findFirstByRuche_IdOrderByDateComptageDescIdDesc(any()))
                    .thenReturn(Optional.of(c));
            return calculer(visite());
        }

        @Test
        @DisplayName("un comptage à traiter retire trente points, indépendamment de la visite")
        void aTraiter() {
            IndiceColonie indice = avecComptage(
                    comptage("lange", 120, null, 3, JOUR.minusDays(2)));

            // Le varroa se lit sur son propre comptage : c'est souvent un lange
            // posé entre deux passages.
            assertThat(indice.composantes()).isEqualTo(1);
            assertThat(indice.sante()).isEqualTo(70);
            assertThat(indice.motifs()).containsExactly("varroa_a_traiter");
        }

        @Test
        @DisplayName("un comptage inexploitable ne compte pas comme une composante")
        void verdictInconnu() {
            // Ni abeilles échantillonnées ni jours d'exposition : le taux ne se
            // calcule pas. Compter cela comme une observation ferait croire que
            // le varroa a été évalué.
            IndiceColonie indice = avecComptage(
                    comptage("echantillon", 5, null, null, JOUR.minusDays(2)));

            assertThat(indice.composantes()).isZero();
            assertThat(indice.nonEvalue()).isTrue();
        }

        @Test
        @DisplayName("un comptage trop vieux est ignoré, comme une visite trop vieille")
        void comptageHorsFraicheur() {
            IndiceColonie indice = avecComptage(
                    comptage("lange", 120, null, 3, JOUR.minusDays(46)));

            assertThat(indice.composantes()).isZero();
        }
    }

    // ── Risque d'essaimage ──────────────────────────────────────────────────

    @Nested
    @DisplayName("risque d'essaimage")
    class Essaimage {

        @Test
        @DisplayName("des cellules d'ESSAIMAGE pèsent le double de cellules d'autre cause")
        void causeDesCellules() {
            Visite essaimage = visite();
            essaimage.setCellulesRoyales(3);
            essaimage.setCellulesRoyalesCause("essaimage");
            Visite supersedure = visite();
            supersedure.setCellulesRoyales(3);
            supersedure.setCellulesRoyalesCause("supersedure");

            // 60 + 6 contre 30 + 6 : la colonie a décidé, ou elle remplace sa
            // reine. Ce ne sont pas les mêmes gestes le lendemain.
            assertThat(calculer(essaimage).risqueEssaimage()).isEqualTo(66);
            monter();
            assertThat(calculer(supersedure).risqueEssaimage()).isEqualTo(36);
        }

        @Test
        @DisplayName("le nombre de cellules compte, et son apport est plafonné")
        void nombreDeCellulesPlafonne() {
            Visite dix = visite();
            dix.setCellulesRoyales(10);
            dix.setCellulesRoyalesCause("essaimage");
            Visite vingt = visite();
            vingt.setCellulesRoyales(20);
            vingt.setCellulesRoyalesCause("essaimage");

            // Dix cellules ne sont pas deux cellules ; mais vingt ne sont pas
            // deux fois dix — au-delà, le signal ne se précise plus.
            assertThat(calculer(dix).risqueEssaimage()).isEqualTo(80);
            monter();
            assertThat(calculer(vingt).risqueEssaimage()).isEqualTo(80);
        }

        @Test
        @DisplayName("zéro cellule royale n'ajoute rien")
        void aucuneCellule() {
            Visite v = visite();
            v.setCellulesRoyales(0);

            assertThat(calculer(v).risqueEssaimage()).isZero();
        }

        @Test
        @DisplayName("un corps plein de couvain est la cause, pas le signe")
        void couvainAbondant() {
            Visite huit = visite();
            huit.setCadresCouvain(8);
            Visite sept = visite();
            sept.setCadresCouvain(7);

            assertThat(calculer(huit).risqueEssaimage()).isEqualTo(20);
            monter();
            assertThat(calculer(sept).risqueEssaimage()).isZero();
        }

        @Test
        @DisplayName("couvain operculé et réserves hautes : le contexte, dix points")
        void contexteDeBlocage() {
            Visite v = visite();
            v.setCouvainOpercule(true);
            v.setCadresMiel(7);

            assertThat(calculer(v).risqueEssaimage()).isEqualTo(10);
        }

        @Test
        @DisplayName("réserves hautes sans couvain operculé : rien")
        void reservesSeules() {
            Visite v = visite();
            v.setCouvainOpercule(false);
            v.setCadresMiel(7);

            assertThat(calculer(v).risqueEssaimage()).isZero();
        }

        @Test
        @DisplayName("le risque est plafonné à cent")
        void plafondCent() {
            Visite v = visite();
            v.setCellulesRoyales(12);
            v.setCellulesRoyalesCause("essaimage");
            v.setCadresCouvain(9);
            v.setCouvainOpercule(true);
            v.setCadresMiel(8);

            // 60 + 20 + 20 + 10 = 110 : au-delà de cent, l'échelle n'a plus de
            // sens et le nombre cesse de se comparer d'une ruche à l'autre.
            assertThat(calculer(v).risqueEssaimage()).isEqualTo(100);
        }
    }

    // ── Le parc ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le parc classe du plus préoccupant au plus sain, les inconnues en dernier")
    void ordreDuParc() {
        Ruche saine = new Ruche("Saine", null, null, EtatRuche.ACTIVE);
        Ruche malade = new Ruche("Malade", null, null, EtatRuche.ACTIVE);
        Ruche inconnue = new Ruche("Inconnue", null, null, EtatRuche.ACTIVE);
        when(ruches.findAll()).thenReturn(List.of(saine, inconnue, malade));

        Visite bonne = visite();
        bonne.setEtatSante(EtatSante.BON);
        Visite mauvaise = visite();
        mauvaise.setEtatSante(EtatSante.MAUVAIS);
        when(visites.findFirstByRuche_IdOrderByDateVisiteDescIdDesc(any()))
                .thenReturn(Optional.of(mauvaise), Optional.empty(), Optional.of(bonne));

        List<IndiceColonie> parc = service.parc();

        // Les colonies sans observation FERMENT la liste : les mêler aux bonnes
        // notes ferait passer l'ignorance pour de la santé, et l'apiculteur
        // cesserait de les visiter.
        assertThat(parc).hasSize(3);
        assertThat(parc.get(parc.size() - 1).nonEvalue()).isTrue();
        assertThat(parc.get(0).sante()).isLessThanOrEqualTo(parc.get(1).sante());
    }

    @Test
    @DisplayName("une ruche inconnue est refusée, pas rendue vide")
    void rucheIntrouvable() {
        when(ruches.findById(999L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.pourRuche(999L))
                .isInstanceOf(com.zumm.web.RessourceIntrouvable.class);
    }

    @Test
    @DisplayName("pourRuche calcule sur la ruche demandée")
    void pourRuche() {
        Ruche r = ruche();
        when(ruches.findById(7L)).thenReturn(Optional.of(r));
        Visite v = visite();
        v.setEtatSante(EtatSante.MOYEN);
        when(visites.findFirstByRuche_IdOrderByDateVisiteDescIdDesc(any()))
                .thenReturn(Optional.of(v));

        IndiceColonie indice = service.pourRuche(7L);

        assertThat(indice.sante()).isEqualTo(85);
        assertThat(indice.rucheModele()).isEqualTo("Dadant");
    }
}
