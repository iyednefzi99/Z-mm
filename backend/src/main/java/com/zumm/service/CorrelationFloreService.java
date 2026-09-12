package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.Ruche;
import com.zumm.repository.CouvertSolRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.dto.CorrelationFlore;
import com.zumm.web.dto.IndiceColonie;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Croisement sante des colonies × flore environnante (SPRINT-33).
 *
 * <p>Ferme la derniere ligne 🟡 du §2 de {@code docs/ECART-CONCURRENTS.md}. Les
 * deux moities existaient depuis le SPRINT-32 — surfaces par classe autour du
 * rucher d'un cote, indices de colonie du SPRINT-22 de l'autre — et se lisaient
 * cote a cote sans que rien ne les relie.
 *
 * <p><strong>Pourquoi ce calcul avait ete refuse, et ce qui a change.</strong> Le
 * refus tenait en une phrase : « sur la dizaine de ruchers d'une exploitation,
 * une correlation serait du bruit presente comme un resultat ». L'objection
 * portait sur la PRESENTATION, pas sur le calcul — et c'est exactement ce que
 * {@link Coefficient} sait refuser depuis le SPRINT-22 pour la meteo. Le service
 * calcule donc, et <strong>n'interprete rien</strong> en dessous de
 * {@value Coefficient#ECHANTILLON_MINIMAL} ruchers : le coefficient sort nu, avec
 * son echantillon, et le tableau de bord affiche « echantillon insuffisant » au
 * lieu d'un verdict. Une exploitation de huit ruchers ne lira jamais « lien
 * marque » ici.
 *
 * <p><strong>L'unite d'observation est le RUCHER, pas la ruche.</strong> C'est le
 * rucher qui porte un environnement ; apparier chaque colonie ferait entrer
 * quarante fois le meme couvert dans le calcul et gonflerait l'echantillon d'un
 * facteur quarante sans ajouter la moindre information sur la flore. La sante du
 * rucher est la MOYENNE de ses colonies evaluees.
 *
 * <p><strong>Dix coefficients sur les memes ruchers.</strong> La taxonomie compte
 * dix classes, toutes mesurees sur le meme echantillon : c'est une comparaison
 * multiple, et sur douze points l'une d'elles franchira 0,5 par le seul hasard.
 * Le service ne corrige pas ce biais — le corriger demanderait un modele, et un
 * modele demanderait des donnees de validation que personne n'a ici. Il le NOMME,
 * et l'ecran porte l'avertissement. Un coefficient qu'on ne sait pas corriger se
 * montre avec sa limite, il ne se cache pas et ne se maquille pas.
 *
 * <p>Et le rappel qui vaut pour les deux correlations du produit : un lien n'est
 * pas une cause. Des ruchers en foret qui vont bien ne prouvent pas que la foret
 * soigne les abeilles — peut-etre y met-on les colonies les plus fortes.
 */
@Service
@Transactional(readOnly = true)
public class CorrelationFloreService {

    private final RucheRepository ruches;
    private final CouvertSolRepository couverts;
    private final IndiceColonieService indices;
    private final ConfigurationMetier configuration;

    public CorrelationFloreService(RucheRepository ruches, CouvertSolRepository couverts,
            IndiceColonieService indices, ConfigurationMetier configuration) {
        this.ruches = ruches;
        this.couverts = couverts;
        this.indices = indices;
        this.configuration = configuration;
    }

    /**
     * Un coefficient par classe de couvert, sur le millesime demande.
     *
     * <p>Sans millesime, le plus recent verse : comparer la sante d'aujourd'hui a
     * l'occupation du sol de 2019 croiserait deux etats qui n'ont jamais coexiste.
     */
    public List<CorrelationFlore> calculer(Integer millesimeDemande) {
        List<Integer> disponibles = couverts.millesimes();
        Integer millesime = millesimeDemande != null ? millesimeDemande
                : disponibles.isEmpty() ? null : disponibles.get(0);
        if (millesime == null) {
            // Aucune couche versee : rien a correler, et l'ecran doit le dire au
            // lieu d'afficher dix lignes vides qui se liraient comme dix absences
            // de lien.
            return List.of();
        }

        Map<Long, Double> santeParRucher = santeParRucher();
        Map<Long, Map<String, BigDecimal>> partsParRucher =
                partsParRucher(millesime, santeParRucher.keySet());

        List<CorrelationFlore> resultat = new ArrayList<>();
        for (String classe : classes(partsParRucher)) {
            List<double[]> points = new ArrayList<>();
            for (Map.Entry<Long, Double> rucher : santeParRucher.entrySet()) {
                Map<String, BigDecimal> parts = partsParRucher.get(rucher.getKey());
                if (parts == null) {
                    // Rucher hors de toute couche versee : son environnement est
                    // INCONNU, pas vide. Le compter pour 0 % de cultures ferait
                    // entrer une ignorance dans le calcul comme une mesure.
                    continue;
                }
                // Une classe absente du rucher vaut bien 0 % : la couche decrit
                // ce cercle-la, et n'y a pas trouve de vigne.
                double part = parts.getOrDefault(classe, BigDecimal.ZERO).doubleValue();
                points.add(new double[] {part, rucher.getValue()});
            }
            Coefficient.Lecture lecture = Coefficient.de(points);
            resultat.add(new CorrelationFlore(classe, lecture.coefficient(), points.size(),
                    lecture.code()));
        }
        return resultat;
    }

    /**
     * Sante moyenne des colonies EVALUEES, par rucher.
     *
     * <p>Les colonies sans observation exploitable sont ecartees, pas comptees
     * pour zero : une ruche jamais ouverte n'est pas une ruche malade, et la
     * faire entrer a 0 ferait plonger la sante des ruchers les moins visites —
     * c'est-a-dire ceux dont on sait le moins. Un rucher dont aucune colonie n'est
     * evaluee ne participe a aucun coefficient.
     */
    private Map<Long, Double> santeParRucher() {
        Map<Long, IndiceColonie> parRuche = indices.parc().stream()
                .filter(indice -> !indice.nonEvalue())
                .collect(Collectors.toMap(IndiceColonie::rucheId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));

        Map<Long, List<Integer>> santes = new LinkedHashMap<>();
        for (Ruche ruche : ruches.findAll()) {
            IndiceColonie indice = parRuche.get(ruche.getId());
            if (indice == null || ruche.getSite() == null) {
                continue;
            }
            santes.computeIfAbsent(ruche.getSite().getId(), cle -> new ArrayList<>())
                    .add(indice.sante());
        }

        Map<Long, Double> moyennes = new LinkedHashMap<>();
        santes.forEach((siteId, valeurs) -> moyennes.put(siteId,
                valeurs.stream().mapToInt(Integer::intValue).average().orElseThrow()));
        return moyennes;
    }

    /**
     * Part de chaque classe dans le cercle de butinage, par rucher, en pourcent.
     *
     * <p>La PART et non la surface : deux ruchers dont les rayons different n'ont
     * pas le meme cercle, et correler des hectares reviendrait a correler la
     * taille du rayon.
     */
    private Map<Long, Map<String, BigDecimal>> partsParRucher(int millesime,
            java.util.Set<Long> ruchersRetenus) {
        Map<Long, Map<String, BigDecimal>> parts = new LinkedHashMap<>();
        for (CouvertSolRepository.PartSite ligne
                : couverts.partsParSite(millesime, configuration.seuils().rayonButinageKm())) {
            if (!ruchersRetenus.contains(ligne.siteId())) {
                continue;
            }
            BigDecimal cercleHa = surfaceCercleHa(ligne.rayonKm());
            BigDecimal part = cercleHa.signum() == 0 ? BigDecimal.ZERO
                    : ligne.surfaceHa().multiply(BigDecimal.valueOf(100))
                            .divide(cercleHa, 2, RoundingMode.HALF_UP);
            parts.computeIfAbsent(ligne.siteId(), cle -> new LinkedHashMap<>())
                    .put(ligne.classe(), part);
        }
        return parts;
    }

    /** Surface du cercle de butinage en hectares, a rayon donne en kilometres. */
    private BigDecimal surfaceCercleHa(BigDecimal rayonKm) {
        double rayonM = rayonKm.doubleValue() * 1000;
        return BigDecimal.valueOf(Math.PI * rayonM * rayonM / 10_000)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Classes reellement decrites par la couche, dans un ordre stable.
     *
     * <p>Rendre les dix classes de la taxonomie ferait sortir six lignes
     * « echantillon insuffisant » pour des couverts absents du territoire — du
     * bruit d'affichage qui ferait passer les quatre lignes utiles au second plan.
     */
    private List<String> classes(Map<Long, Map<String, BigDecimal>> parts) {
        return parts.values().stream()
                .flatMap(m -> m.keySet().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
