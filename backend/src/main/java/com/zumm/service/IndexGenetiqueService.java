package com.zumm.service;

import com.zumm.domain.ComptageVarroa;
import com.zumm.domain.Recolte;
import com.zumm.domain.Reine;
import com.zumm.domain.Visite;
import com.zumm.repository.ComptageVarroaRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.ReleveObservationRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.IndexGenetique;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Index genetique multicritere d'une reine (SPRINT-29, lot D).
 *
 * <p>Ferme la ligne « index génétique multicritère » du §7, dont la dependance
 * etait levee depuis le SPRINT-20 : les criteres d'inspection dont il decoule
 * existent.
 *
 * <p><strong>Aucune note globale.</strong> C'est la decision du lot, et elle est
 * la meme qu'au SPRINT-23 pour la comparaison d'emplacements : la douceur, un
 * taux d'infestation et des kilogrammes ne s'additionnent pas. Les ramener a un
 * seul nombre demanderait une ponderation que personne n'a demandee, et qui
 * serait pourtant le seul chiffre retenu.
 *
 * <p><strong>Rien n'est stocke.</strong> Tout se recalcule a chaque lecture,
 * comme les indices de colonie du SPRINT-22 et le taux de varroa du SPRINT-20.
 *
 * <p><strong>Le regne borne tout.</strong> Une reine n'herite pas de ce qui
 * s'est passe avant son introduction : sans cette borne, une reine posee en
 * juillet recevrait la recolte de printemps de la precedente, et le classement
 * de l'eleveur serait exactement inverse.
 */
@Service
public class IndexGenetiqueService {

    /**
     * Observations minimales par critere.
     *
     * <p>Trois pour ce qui s'apprecie a l'oeil — une douceur mesuree sur une
     * seule visite n'est pas une douceur, c'est un jour de vent —, deux pour ce
     * qui se compte, une pour ce qui se pese.
     */
    private static final int MIN_APPRECIATION = 3;
    private static final int MIN_COMPTAGE = 2;

    /** Code du point d'observation entre au referentiel par la V29. */
    private static final String POINT_HYGIENE = "test_hygienique";

    private static final Map<String, Integer> DOUCEUR = Map.of(
            "doux", 3, "normal", 2, "agressif", 1);

    private final VisiteRepository visites;
    private final RecolteRepository recoltes;
    private final ComptageVarroaRepository comptages;
    private final ReleveObservationRepository releves;

    public IndexGenetiqueService(VisiteRepository visites, RecolteRepository recoltes,
            ComptageVarroaRepository comptages, ReleveObservationRepository releves) {
        this.visites = visites;
        this.recoltes = recoltes;
        this.comptages = comptages;
        this.releves = releves;
    }

    /**
     * Les criteres observes pendant le regne d'une reine.
     *
     * <p>Sans ruche, aucun critere : tout ce qui se mesure ici se mesure sur une
     * colonie. Une reine en banque a reines n'a pas d'index, et le dire vaut
     * mieux que rendre cinq zeros.
     */
    @Transactional(readOnly = true)
    public IndexGenetique pour(Reine reine) {
        LocalDate debut = reine.getDateIntroduction() == null
                ? LocalDate.of(2000, 1, 1)
                : reine.getDateIntroduction();
        LocalDate fin = reine.getDateFin() == null ? LocalDate.now() : reine.getDateFin();
        Long rucheId = reine.getRuche() == null ? null : reine.getRuche().getId();

        List<IndexGenetique.Critere> criteres = rucheId == null
                ? List.of()
                : calculer(rucheId, debut, fin);
        return new IndexGenetique(reine.getId(), com.zumm.web.dto.ReineElevage.nom(reine),
                rucheId, debut, fin, criteres);
    }

    private List<IndexGenetique.Critere> calculer(Long rucheId, LocalDate debut, LocalDate fin) {
        List<Visite> pendantLeRegne =
                visites.findByRuche_IdAndDateVisiteBetweenOrderByDateVisiteAsc(rucheId, debut, fin);
        List<IndexGenetique.Critere> criteres = new ArrayList<>();
        criteres.add(douceur(pendantLeRegne));
        criteres.add(essaimage(pendantLeRegne));
        criteres.add(resistanceVarroa(rucheId, debut, fin));
        criteres.add(production(rucheId, debut, fin));
        criteres.add(hygiene(rucheId, debut, fin));
        return criteres;
    }

    /** Temperament moyen, de 1 (agressif) a 3 (doux). */
    private IndexGenetique.Critere douceur(List<Visite> pendantLeRegne) {
        List<Integer> notes = pendantLeRegne.stream()
                .map(Visite::getTemperament)
                .filter(t -> t != null && DOUCEUR.containsKey(t))
                .map(DOUCEUR::get)
                .toList();
        if (notes.size() < MIN_APPRECIATION) {
            return IndexGenetique.Critere.insuffisant("douceur", "sur 3", notes.size());
        }
        double moyenne = notes.stream().mapToInt(Integer::intValue).average().orElse(0);
        return new IndexGenetique.Critere("douceur",
                BigDecimal.valueOf(moyenne).setScale(1, RoundingMode.HALF_UP),
                "sur 3", notes.size(), true);
    }

    /**
     * Visites ou des cellules royales d'essaimage ont ete comptees.
     *
     * <p>Le denominateur est le nombre de visites du regne, pas le nombre de
     * jours : c'est une frequence d'observation, et la presenter autrement
     * ferait croire a une probabilite.
     */
    private IndexGenetique.Critere essaimage(List<Visite> pendantLeRegne) {
        if (pendantLeRegne.size() < MIN_APPRECIATION) {
            return IndexGenetique.Critere.insuffisant("essaimage", "visites",
                    pendantLeRegne.size());
        }
        long episodes = pendantLeRegne.stream()
                .filter(v -> v.getCellulesRoyales() != null && v.getCellulesRoyales() > 0)
                .filter(v -> "essaimage".equals(v.getCellulesRoyalesCause()))
                .count();
        return new IndexGenetique.Critere("essaimage", BigDecimal.valueOf(episodes),
                "visites", pendantLeRegne.size(), true);
    }

    /**
     * Infestation moyenne, dans l'unite d'UNE methode.
     *
     * <p><strong>Les methodes ne se melangent pas.</strong> Un lange donne des
     * varroas par jour, un echantillon un pourcentage : le SPRINT-20 avait deja
     * refuse de les confondre, et en faire une moyenne unique ici defairait
     * exactement ce refus. On retient donc la methode la plus employee pendant
     * le regne, et l'unite le dit.
     */
    private IndexGenetique.Critere resistanceVarroa(Long rucheId, LocalDate debut, LocalDate fin) {
        List<ComptageVarroa> tous = comptages
                .findByRuche_IdAndDateComptageBetweenOrderByDateComptageAsc(rucheId, debut, fin);
        if (tous.isEmpty()) {
            return IndexGenetique.Critere.insuffisant("resistance_varroa", null, 0);
        }
        Map<String, List<ComptageVarroa>> parMethode = tous.stream()
                .collect(Collectors.groupingBy(ComptageVarroa::getMethode));
        List<ComptageVarroa> retenus = parMethode.values().stream()
                .max(Comparator.comparingInt(List::size))
                .orElseThrow();
        String unite = retenus.get(0).parLange() ? "varroas/jour" : "%";
        if (retenus.size() < MIN_COMPTAGE) {
            return IndexGenetique.Critere.insuffisant("resistance_varroa", unite, retenus.size());
        }
        BigDecimal somme = retenus.stream()
                .map(ComptageVarroaService::taux)
                .filter(t -> t != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new IndexGenetique.Critere("resistance_varroa",
                somme.divide(BigDecimal.valueOf(retenus.size()), 2, RoundingMode.HALF_UP),
                unite, retenus.size(), true);
    }

    /**
     * Miel recolte pendant le regne.
     *
     * <p>Le miel SEUL : additionner des kilogrammes de cire et des essaims
     * comptes a l'unite donnerait un total qui ne veut rien dire — c'est la
     * meme regle qu'a la valorisation du SPRINT-27.
     */
    private IndexGenetique.Critere production(Long rucheId, LocalDate debut, LocalDate fin) {
        List<Recolte> pendantLeRegne = recoltes
                .findByRuche_IdAndDateRecolteBetweenOrderByDateRecolteAsc(rucheId, debut, fin)
                .stream().filter(Recolte::estDuMiel).toList();
        if (pendantLeRegne.isEmpty()) {
            return IndexGenetique.Critere.insuffisant("production", "kg", 0);
        }
        BigDecimal total = pendantLeRegne.stream()
                .map(Recolte::getQuantiteKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new IndexGenetique.Critere("production", total.setScale(1, RoundingMode.HALF_UP),
                "kg", pendantLeRegne.size(), true);
    }

    /**
     * Test hygienique, de 0 a 3.
     *
     * <p>Le seul critere du §7 dont aucune donnee n'existait. Il est entre par
     * la porte prevue : la V29 ajoute un point au referentiel ferme du
     * SPRINT-28. Tant que personne ne l'a releve, il se dit insuffisant — ce qui
     * est la reponse juste, et non un zero.
     */
    private IndexGenetique.Critere hygiene(Long rucheId, LocalDate debut, LocalDate fin) {
        List<Short> notes = releves.intensitesPour(POINT_HYGIENE, rucheId, debut, fin);
        if (notes.size() < MIN_COMPTAGE) {
            return IndexGenetique.Critere.insuffisant("hygiene", "sur 3", notes.size());
        }
        double moyenne = notes.stream().mapToInt(Short::intValue).average().orElse(0);
        return new IndexGenetique.Critere("hygiene",
                BigDecimal.valueOf(moyenne).setScale(1, RoundingMode.HALF_UP),
                "sur 3", notes.size(), true);
    }
}
