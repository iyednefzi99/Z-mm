package com.zumm.service;

import com.zumm.domain.LotComposition;
import com.zumm.domain.LotConditionnement;
import com.zumm.domain.Recolte;
import com.zumm.repository.LotConditionnementRepository;
import com.zumm.repository.RecolteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.LotCorps;
import com.zumm.web.dto.LotReponse;
import com.zumm.web.dto.MentionOrigine;
import com.zumm.web.dto.OrigineDeclaree;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lots de conditionnement et mention d'origine (US-056).
 *
 * <p>Met en oeuvre la directive (UE) 2024/1438, applicable au 14 juin 2026 :
 * l'etiquette d'un pot de miel porte le ou les pays d'origine, par ordre
 * DECROISSANT de proportion, chaque part chiffree en pourcentage.
 *
 * <p>Deux regles portent tout le reste :
 *
 * <ol>
 *   <li><strong>la somme des parts fait 100 %.</strong> Verifiee ici, avec un
 *       message qui dit de combien on s'ecarte — pas dans un trigger, qui
 *       renverrait une erreur SQL illisible a l'utilisateur ;
 *   <li><strong>les parts se consolident PAR PAYS avant d'etre triees.</strong>
 *       Trois recoltes francaises a 20 % ne s'ecrivent pas « France 20 %, France
 *       20 %, France 20 % » mais « France 60 % ». C'est ce que lit un
 *       consommateur, et c'est ce que verifie un controle.
 * </ol>
 */
@Service
@Transactional
public class LotConditionnementService {

    /**
     * Tolerance de la directive : 5 points par part, calculee sur les documents de
     * tracabilite de l'operateur. Elle s'applique a l'ECART d'une part declaree,
     * pas a la somme — d'ou une tolerance de somme volontairement etroite, qui ne
     * couvre que les arrondis de saisie.
     */
    private static final BigDecimal TOLERANCE_SOMME = new BigDecimal("0.05");

    private static final BigDecimal CENT = new BigDecimal("100");

    private final LotConditionnementRepository lots;
    private final RecolteRepository recoltes;

    public LotConditionnementService(LotConditionnementRepository lots, RecolteRepository recoltes) {
        this.lots = lots;
        this.recoltes = recoltes;
    }

    @Transactional(readOnly = true)
    public List<LotReponse> lister() {
        return lots.findByOrderByDateConditionnementDescIdDesc().stream().map(LotReponse::de).toList();
    }

    @Transactional(readOnly = true)
    public LotReponse obtenir(Long id) {
        return LotReponse.de(entite(id));
    }

    public LotReponse creer(LotCorps corps) {
        if (lots.existsByReference(corps.reference())) {
            throw new RequeteInvalide(
                    "La reference de lot « " + corps.reference() + " » est deja utilisee.");
        }
        LotConditionnement lot = new LotConditionnement(
                corps.reference(), corps.dateConditionnement(), corps.quantiteKg());
        appliquer(lot, corps);
        return LotReponse.de(lots.save(lot));
    }

    public LotReponse mettreAJour(Long id, LotCorps corps) {
        LotConditionnement lot = entite(id);
        if (!lot.getReference().equals(corps.reference()) && lots.existsByReference(corps.reference())) {
            throw new RequeteInvalide(
                    "La reference de lot « " + corps.reference() + " » est deja utilisee.");
        }
        lot.setReference(corps.reference());
        lot.setDateConditionnement(corps.dateConditionnement());
        lot.setQuantiteKg(corps.quantiteKg());
        appliquer(lot, corps);
        return LotReponse.de(lot);
    }

    public void supprimer(Long id) {
        lots.delete(entite(id));
    }

    /**
     * Mention d'origine d'un lot, prete a imprimer.
     *
     * <p>La forme suit la directive : un pays unique donne « Origine : France » ;
     * un melange donne la liste decroissante avec les pourcentages. Le libelle du
     * pays est rendu dans la locale demandee — l'etiquette d'un miel exporte
     * s'imprime dans la langue du marche, pas dans celle du producteur.
     */
    @Transactional(readOnly = true)
    public MentionOrigine mention(Long id, Locale locale) {
        LotConditionnement lot = entite(id);
        List<MentionOrigine.Part> parts = consolider(lot.getComposition(), locale);

        String texte = parts.size() == 1
                ? "Origine : " + parts.get(0).libelle()
                : "Origine : " + parts.stream()
                        .map(p -> p.libelle() + " " + p.pourcentage().stripTrailingZeros().toPlainString() + " %")
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
        return new MentionOrigine(texte, parts, parts.size() > 1);
    }

    /**
     * Regroupe les parts par pays, les trie par proportion decroissante puis, a
     * egalite, par code pays — sans quoi deux lots identiques pourraient produire
     * deux etiquettes d'ordre different selon l'ordre de lecture en base.
     */
    private List<MentionOrigine.Part> consolider(List<LotComposition> composition, Locale locale) {
        Map<String, BigDecimal> parPays = new LinkedHashMap<>();
        for (LotComposition part : composition) {
            parPays.merge(part.getPaysOrigine(), part.getPourcentage(), BigDecimal::add);
        }
        return parPays.entrySet().stream()
                .map(e -> new MentionOrigine.Part(
                        e.getKey(),
                        new Locale.Builder().setRegion(e.getKey()).build().getDisplayCountry(locale),
                        e.getValue().setScale(0, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(MentionOrigine.Part::pourcentage).reversed()
                        .thenComparing(MentionOrigine.Part::paysOrigine))
                .toList();
    }

    /** Reconstitue integralement la composition apres l'avoir validee. */
    private void appliquer(LotConditionnement lot, LotCorps corps) {
        verifierSomme(corps.origines());
        lot.setTypeMiel(corps.typeMiel());
        lot.setNote(corps.note());
        lot.viderComposition();
        for (OrigineDeclaree origine : corps.origines()) {
            lot.ajouter(new LotComposition(
                    recolteEventuelle(origine.recolteId()),
                    origine.paysOrigine(),
                    origine.pourcentage()));
        }
    }

    private void verifierSomme(List<OrigineDeclaree> origines) {
        BigDecimal somme = origines.stream()
                .map(OrigineDeclaree::pourcentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (somme.subtract(CENT).abs().compareTo(TOLERANCE_SOMME) > 0) {
            // Le message donne l'ecart : sans lui, l'utilisateur cherche a l'oeil
            // laquelle des huit parts est fausse.
            throw new RequeteInvalide(
                    "Les parts d'origine totalisent " + somme.stripTrailingZeros().toPlainString()
                    + " % au lieu de 100 % (ecart de "
                    + somme.subtract(CENT).stripTrailingZeros().toPlainString() + " point(s)).");
        }
    }

    private Recolte recolteEventuelle(Long recolteId) {
        if (recolteId == null) {
            // Miel acquis a un tiers : declare par sa seule origine.
            return null;
        }
        return recoltes.findById(recolteId).orElseThrow(() ->
                new RequeteInvalide("Recolte inconnue dans ce tenant : " + recolteId));
    }

    private LotConditionnement entite(Long id) {
        return lots.findById(id).orElseThrow(() -> RessourceIntrouvable.de("Lot", id));
    }
}
