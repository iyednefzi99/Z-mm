package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.Depense;
import com.zumm.domain.Recolte;
import com.zumm.domain.Ruche;
import com.zumm.repository.DepenseRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.SiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.BilanExploitation;
import com.zumm.web.dto.DepenseCorps;
import com.zumm.web.dto.DepenseReponse;
import com.zumm.web.dto.RentabiliteRuche;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dépenses et rentabilité par ruche (SPRINT-27, lot E).
 *
 * <p>Ferme le 🟡 du §6 : « `SyntheseService` calcule un ROI global depuis
 * `ConfigZumm.ini` ; pas de coûts réels, pas de ventilation par ruche ou par
 * rucher ». Le ROI reposait sur une hypothèse — un coût moyen par visite — ; le
 * bilan repose sur ce qui a réellement été dépensé.
 *
 * <p><strong>Trois refus, et ils tiennent le module.</strong>
 *
 * <ol>
 *   <li>Il ne <strong>répartit aucune dépense non affectée</strong>. Une
 *       assurance, une formation, un véhicule ne se divisent pas par le nombre
 *       de ruches : la clé de répartition serait inventée, et le résultat aurait
 *       l'autorité d'un chiffre sans en avoir la matière. Elles figurent à part,
 *       entières, où elles se voient ;
 *   <li>les recettes sont une <strong>valorisation</strong>, pas un chiffre
 *       d'affaires. Zümm ne sait pas à quel prix le miel a été vendu, et ne
 *       cherche pas à le savoir — la facturation est hors périmètre (§9) ;
 *   <li>seul le <strong>miel</strong> entre dans la valorisation. Additionner
 *       des kilos de cire, de pollen et des essaims à un prix du miel donnerait
 *       un total qui ne veut rien dire ; la production des autres produits se
 *       lit dans la comparaison de saisons, produit par produit.
 * </ol>
 */
@Service
@Transactional
public class ComptabiliteService {

    private final DepenseRepository depenses;
    private final RecolteRepository recoltes;
    private final RucheRepository ruches;
    private final SiteRepository sites;
    private final ConfigurationMetier configuration;

    public ComptabiliteService(DepenseRepository depenses, RecolteRepository recoltes,
            RucheRepository ruches, SiteRepository sites, ConfigurationMetier configuration) {
        this.depenses = depenses;
        this.recoltes = recoltes;
        this.ruches = ruches;
        this.sites = sites;
        this.configuration = configuration;
    }

    // ─── Saisie ──────────────────────────────────────────────────────────────

    public DepenseReponse creer(DepenseCorps corps) {
        Depense depense = new Depense(corps.libelle().trim(), corps.categorie(),
                corps.montantEur(), corps.dateDepense());
        appliquer(depense, corps);
        return DepenseReponse.de(depenses.save(depense));
    }

    @Transactional(readOnly = true)
    public List<DepenseReponse> lister() {
        return depenses.findAllByOrderByDateDepenseDescIdDesc().stream()
                .map(DepenseReponse::de)
                .toList();
    }

    public DepenseReponse mettreAJour(Long id, DepenseCorps corps) {
        Depense depense = entite(id);
        depense.setLibelle(corps.libelle().trim());
        depense.setCategorie(corps.categorie());
        depense.setMontantEur(corps.montantEur());
        depense.setDateDepense(corps.dateDepense());
        appliquer(depense, corps);
        return DepenseReponse.de(depense);
    }

    public void supprimer(Long id) {
        depenses.delete(entite(id));
    }

    // ─── Bilan ───────────────────────────────────────────────────────────────

    /**
     * Bilan d'une période, dépenses ventilées et rentabilité ruche par ruche.
     *
     * <p>La période est <strong>demandée</strong>, jamais devinée : un bilan sur
     * « les douze derniers mois » recule chaque jour, et deux consultations à une
     * semaine d'écart ne portent alors pas sur la même chose.
     */
    @Transactional(readOnly = true)
    public BilanExploitation bilan(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw new RequeteInvalide("La période demandée est vide ou inversée.");
        }
        BigDecimal prixKg = configuration.seuils().prixMielKgEur();

        List<Depense> periode = depenses.findByDateDepenseBetweenOrderByDateDepenseDescIdDesc(
                debut, fin);
        List<Recolte> production = recoltes.findByOrderByDateRecolteDescIdDesc().stream()
                .filter(r -> !r.getDateRecolte().isBefore(debut)
                        && !r.getDateRecolte().isAfter(fin))
                .toList();

        // Seul le MIEL est valorisé : le prix du kilo de ConfigZumm.ini est un
        // prix de miel, et l'appliquer à de la cire ou à un essaim donnerait un
        // chiffre inventé.
        Map<Long, BigDecimal> mielParRuche = production.stream()
                .filter(Recolte::estDuMiel)
                .collect(Collectors.groupingBy(r -> r.getRuche().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Recolte::getQuantiteKg,
                                BigDecimal::add)));

        Map<Long, BigDecimal> depensesParRuche = periode.stream()
                .filter(d -> d.getRuche() != null)
                .collect(Collectors.groupingBy(d -> d.getRuche().getId(),
                        Collectors.reducing(BigDecimal.ZERO, Depense::getMontantEur,
                                BigDecimal::add)));

        BigDecimal totalDepenses = somme(periode.stream().map(Depense::getMontantEur).toList());
        BigDecimal nonAffectees = somme(periode.stream()
                .filter(d -> d.getRuche() == null)
                .map(Depense::getMontantEur)
                .toList());
        BigDecimal mielTotal = somme(mielParRuche.values().stream().toList());
        BigDecimal recettes = mielTotal.multiply(prixKg).setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> parCategorie = periode.stream()
                .collect(Collectors.groupingBy(Depense::getCategorie,
                        Collectors.reducing(BigDecimal.ZERO, Depense::getMontantEur,
                                BigDecimal::add)))
                .entrySet().stream()
                // Du plus gros poste au plus petit : c'est là qu'on agit.
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        List<RentabiliteRuche> parRuche = ruches.findAll().stream()
                .map(ruche -> rentabilite(ruche, mielParRuche, depensesParRuche, prixKg))
                // Le plus rentable d'abord : la question posée est « lesquelles
                // portent l'exploitation », pas « lesquelles ont le plus petit
                // identifiant ».
                .sorted(Comparator.comparing(RentabiliteRuche::resultatEur).reversed())
                .toList();

        return new BilanExploitation(debut, fin,
                mielTotal.setScale(3, RoundingMode.HALF_UP),
                recettes,
                totalDepenses.setScale(2, RoundingMode.HALF_UP),
                recettes.subtract(totalDepenses).setScale(2, RoundingMode.HALF_UP),
                nonAffectees.setScale(2, RoundingMode.HALF_UP),
                parCategorie,
                parRuche);
    }

    private RentabiliteRuche rentabilite(Ruche ruche, Map<Long, BigDecimal> mielParRuche,
            Map<Long, BigDecimal> depensesParRuche, BigDecimal prixKg) {
        BigDecimal miel = mielParRuche.getOrDefault(ruche.getId(), BigDecimal.ZERO);
        BigDecimal recettes = miel.multiply(prixKg).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cout = depensesParRuche.getOrDefault(ruche.getId(), BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        return new RentabiliteRuche(
                ruche.getId(),
                ruche.getModele(),
                ruche.getSite() == null ? null : ruche.getSite().getNom(),
                miel.setScale(3, RoundingMode.HALF_UP),
                recettes,
                cout,
                recettes.subtract(cout));
    }

    private static BigDecimal somme(List<BigDecimal> valeurs) {
        return valeurs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void appliquer(Depense depense, DepenseCorps corps) {
        depense.setRuche(corps.rucheId() == null ? null
                : ruches.findById(corps.rucheId()).orElseThrow(() ->
                        new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId())));
        depense.setSite(corps.siteId() == null ? null
                : sites.findById(corps.siteId()).orElseThrow(() ->
                        new RequeteInvalide("Site inconnu dans ce tenant : " + corps.siteId())));
        depense.setNote(corps.note());
    }

    private Depense entite(Long id) {
        return depenses.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Depense", id));
    }
}
