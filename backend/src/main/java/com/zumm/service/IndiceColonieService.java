package com.zumm.service;

import com.zumm.domain.ComptageVarroa;
import com.zumm.domain.EtatSante;
import com.zumm.domain.ObservationPathologie;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.ComptageVarroaRepository;
import com.zumm.repository.ObservationPathologieRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.IndiceColonie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indice de sante et risque d'essaimage d'une colonie (SPRINT-22, lot A).
 *
 * <p>Repond a trois lignes du §3 de {@code docs/ECART-CONCURRENTS.md} : « score
 * de sante calcule par colonie », « score de risque d'essaimage », et
 * l'agregation de la force de colonie que {@code EffectifQualitatif} laissait a
 * l'etat de curseur a trois positions.
 *
 * <p><strong>Rien n'est stocke.</strong> Les deux indices se calculent a chaque
 * lecture, a partir de colonnes qui existent depuis la V19. C'est le meme parti
 * que pour le taux d'infestation ({@code ComptageVarroaService}) et pour la meme
 * raison : une valeur figee cesse de suivre la formule le jour ou celle-ci
 * change, et la base porte alors deux verites.
 *
 * <p><strong>Un indice n'est pas une mesure, et l'API doit le dire.</strong>
 * Chaque reponse porte le nombre de composantes reellement observees. Une
 * colonie dont on ne sait rien ne recoit pas « 50 sur 100 » — elle recoit un
 * indice nul avec {@code composantes = 0}, et c'est a l'ecran de refuser
 * d'afficher une jauge rassurante sur du vide.
 */
@Service
@Transactional(readOnly = true)
public class IndiceColonieService {

    /** Au-dela, l'observation est trop vieille pour dire l'etat d'aujourd'hui. */
    private static final int FRAICHEUR_JOURS = 45;

    private final RucheRepository ruches;
    private final VisiteRepository visites;
    private final ComptageVarroaRepository comptages;
    private final ObservationPathologieRepository pathologies;

    public IndiceColonieService(RucheRepository ruches, VisiteRepository visites,
            ComptageVarroaRepository comptages, ObservationPathologieRepository pathologies) {
        this.ruches = ruches;
        this.visites = visites;
        this.comptages = comptages;
        this.pathologies = pathologies;
    }

    /** Indices d'une ruche, calcules sur sa derniere visite exploitable. */
    public IndiceColonie pourRuche(Long rucheId) {
        Ruche ruche = ruches.findById(rucheId)
                .orElseThrow(() -> RessourceIntrouvable.de("Ruche", rucheId));
        return calculer(ruche, LocalDate.now());
    }

    /** Indices de tout le parc, du plus preoccupant au plus sain. */
    public List<IndiceColonie> parc() {
        LocalDate jour = LocalDate.now();
        return ruches.findAll().stream()
                .map(ruche -> calculer(ruche, jour))
                // Les colonies sans observation ferment la liste : elles ne sont
                // pas « saines », elles sont inconnues, et les melanger aux
                // bonnes notes ferait passer l'ignorance pour de la sante.
                .sorted((a, b) -> {
                    if (a.composantes() == 0 || b.composantes() == 0) {
                        return Integer.compare(b.composantes() == 0 ? 0 : 1,
                                a.composantes() == 0 ? 0 : 1);
                    }
                    return Integer.compare(a.sante(), b.sante());
                })
                .toList();
    }

    /**
     * Calcule les deux indices.
     *
     * <p>La sante part de 100 et se degrade : c'est plus lisible qu'une somme de
     * points, parce que chaque retrait porte un motif que l'on peut afficher.
     * Une colonie sans observation recente ne perd rien — elle n'a simplement
     * aucune composante, et son indice ne doit pas etre lu.
     */
    IndiceColonie calculer(Ruche ruche, LocalDate jour) {
        Optional<Visite> derniere = visites
                .findFirstByRuche_IdOrderByDateVisiteDescIdDesc(ruche.getId())
                .filter(v -> !v.getDateVisite().isBefore(jour.minusDays(FRAICHEUR_JOURS)));

        List<String> motifs = new ArrayList<>();
        int sante = 100;
        int composantes = 0;

        if (derniere.isPresent()) {
            Visite v = derniere.get();

            if (v.getEtatSante() != null) {
                composantes++;
                if (v.getEtatSante() == EtatSante.MAUVAIS) {
                    sante -= 35;
                    motifs.add("etat_sante_mauvais");
                } else if (v.getEtatSante() == EtatSante.MOYEN) {
                    sante -= 15;
                    motifs.add("etat_sante_moyen");
                }
            }

            // Le couvain operculé est le signe le plus direct d'une ponte qui a
            // eu lieu il y a une dizaine de jours. Son absence AVEC une reine non
            // vue est le couple qui doit inquieter ; separement, chacun s'explique.
            if (v.getCouvainOpercule() != null) {
                composantes++;
                if (Boolean.FALSE.equals(v.getCouvainOpercule())) {
                    sante -= 20;
                    motifs.add("pas_de_couvain_opercule");
                    if (Boolean.FALSE.equals(v.getReineVue())) {
                        sante -= 15;
                        motifs.add("reine_non_vue");
                    }
                }
            }

            if (v.getMotifPonte() != null) {
                composantes++;
                if ("lacunaire".equals(v.getMotifPonte()) || "irregulier".equals(v.getMotifPonte())) {
                    sante -= 10;
                    motifs.add("ponte_" + v.getMotifPonte());
                } else if ("absent".equals(v.getMotifPonte())) {
                    sante -= 25;
                    motifs.add("ponte_absente");
                }
            }

            // Les reserves : un cadre de miel ou moins en fin de saison est le
            // debut d'une famine, pas une variation.
            if (v.getCadresMiel() != null) {
                composantes++;
                if (v.getCadresMiel() <= 1) {
                    sante -= 20;
                    motifs.add("reserves_basses");
                }
            }

            List<ObservationPathologie> observees =
                    pathologies.findByVisite_IdOrderByPathologieAsc(v.getId());
            if (!observees.isEmpty()) {
                composantes++;
                // `suspectee` est la gravite PAR DEFAUT (V19) : au rucher on
                // constate un symptome, on ne pose pas un diagnostic. La compter
                // comme une maladie confirmee ferait chuter l'indice de toute
                // colonie sur laquelle on a eu un doute.
                long graves = observees.stream()
                        .filter(p -> !"suspectee".equals(p.getGravite()))
                        .count();
                if (graves > 0) {
                    sante -= (int) Math.min(40, 20 * graves);
                    motifs.add("pathologie_confirmee");
                }
            }
        }

        // Le varroa se lit sur son propre comptage, independamment de la visite :
        // c'est souvent un lange pose entre deux passages.
        Optional<ComptageVarroa> comptage = comptages
                .findFirstByRuche_IdOrderByDateComptageDescIdDesc(ruche.getId())
                .filter(c -> !c.getDateComptage().isBefore(jour.minusDays(FRAICHEUR_JOURS)));
        if (comptage.isPresent()) {
            // Le VERDICT, jamais le taux brut : celui-ci n'a pas la meme unite
            // selon la methode de comptage — varroas par jour pour un lange,
            // pour-cent d'abeilles pour un echantillon. Comparer un taux a un
            // seuil unique melangerait les deux et se tromperait d'un facteur
            // dix. `ComptageVarroaService` porte deja les deux jeux de seuils.
            String verdict = ComptageVarroaService.verdict(comptage.get());
            if (!"inconnu".equals(verdict)) {
                composantes++;
                if ("traiter".equals(verdict)) {
                    sante -= 30;
                    motifs.add("varroa_a_traiter");
                } else if ("surveiller".equals(verdict)) {
                    sante -= 15;
                    motifs.add("varroa_a_surveiller");
                }
            }
        }

        return new IndiceColonie(
                ruche.getId(),
                ruche.getModele(),
                composantes == 0 ? 0 : Math.max(0, sante),
                risqueEssaimage(derniere),
                composantes,
                derniere.map(Visite::getDateVisite).orElse(null),
                motifs);
    }

    /**
     * Risque d'essaimage, de 0 a 100.
     *
     * <p>Trois signes, et leur poids suit ce qu'ils annoncent : des cellules
     * royales de type ESSAIMAGE sont le signe le plus direct — la colonie a
     * decide —, un corps plein de couvain sans place pour pondre est la cause,
     * et l'absence de hausse au printemps est le contexte.
     *
     * <p>Un indice de 0 quand rien n'est observe, jamais un indice moyen : dire
     * « 50 » sur une colonie non visitee ferait deplacer l'apiculteur pour rien,
     * ou pire, le rassurerait.
     */
    private int risqueEssaimage(Optional<Visite> derniere) {
        if (derniere.isEmpty()) {
            return 0;
        }
        Visite v = derniere.get();
        int risque = 0;
        if (v.getCellulesRoyales() != null && v.getCellulesRoyales() > 0) {
            risque += "essaimage".equals(v.getCellulesRoyalesCause()) ? 60 : 30;
            // Le nombre compte : dix cellules ne sont pas deux cellules.
            risque += Math.min(20, v.getCellulesRoyales() * 2);
        }
        if (v.getCadresCouvain() != null && v.getCadresCouvain() >= 8) {
            risque += 20;
        }
        if (Boolean.TRUE.equals(v.getCouvainOpercule())
                && v.getCadresMiel() != null && v.getCadresMiel() >= 7) {
            risque += 10;
        }
        return Math.min(100, risque);
    }
}
