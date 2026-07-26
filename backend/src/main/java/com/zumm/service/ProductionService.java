package com.zumm.service;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.domain.Ruche;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.LigneProduction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tableau de bord de production : poids par ruche (US-013).
 *
 * <p>Les agregats sont calcules EN BASE depuis le SPRINT-17. L'implementation
 * precedente chargeait toutes les mesures de poids du tenant pour les reduire en
 * memoire : a raison d'un releve par quart d'heure, une exploitation de 500 ruches
 * en produit des dizaines de millions par an. Ce n'etait pas lent, c'etait
 * intenable — la seule lecture aurait sature le tas avant d'agreger quoi que ce
 * soit.
 *
 * <p>Ce service ne fait donc plus que deux choses : demander ses agregats a la
 * base, et les assembler avec les seuils metier.
 */
@Service
@Transactional(readOnly = true)
public class ProductionService {

    private final MesureRepository mesures;
    private final VisiteRepository visites;
    private final RucheRepository ruches;
    private final ConfigurationMetier configuration;

    public ProductionService(MesureRepository mesures, VisiteRepository visites,
            RucheRepository ruches, ConfigurationMetier configuration) {
        this.mesures = mesures;
        this.visites = visites;
        this.ruches = ruches;
        this.configuration = configuration;
    }

    /** Synthese du poids par ruche, drapeau {@code sousSeuil} et productivite moyenne. */
    public List<LigneProduction> production() {
        int seuilKg = configuration.seuils().poidsRucheAlerteKg();

        Map<Long, MesureRepository.AgregatPoids> poids =
                mesures.agregatPoids(TypeIndicateur.POIDS.enBase()).stream()
                        .collect(Collectors.toMap(MesureRepository.AgregatPoids::getRucheId,
                                Function.identity()));
        Map<Long, Double> productivite = visites.productiviteMoyenneParRuche().stream()
                .filter(ligne -> ligne.getMoyenne() != null)
                .collect(Collectors.toMap(VisiteRepository.ProductiviteRuche::getRucheId,
                        VisiteRepository.ProductiviteRuche::getMoyenne));

        List<LigneProduction> lignes = new ArrayList<>();
        for (Ruche ruche : ruches.findAll()) {
            MesureRepository.AgregatPoids p = poids.get(ruche.getId());
            Double moyenne = productivite.get(ruche.getId());
            Double productiviteMoyenne = moyenne == null ? null
                    : arrondi2(BigDecimal.valueOf(moyenne)).doubleValue();

            if (p == null) {
                // Ruche sans aucune mesure : la ligne existe quand meme. La retirer
                // ferait disparaitre du tableau la ruche qu'on surveille le moins.
                lignes.add(new LigneProduction(ruche.getId(), ruche.getModele(),
                        null, null, null, 0, false, productiviteMoyenne));
                continue;
            }
            boolean sousSeuil = p.getActuel().compareTo(BigDecimal.valueOf(seuilKg)) < 0;
            lignes.add(new LigneProduction(ruche.getId(), ruche.getModele(),
                    arrondi2(p.getActuel()), arrondi2(p.getMinimum()), arrondi2(p.getMaximum()),
                    p.getNombre(), sousSeuil, productiviteMoyenne));
        }
        return lignes;
    }

    private static BigDecimal arrondi2(BigDecimal valeur) {
        return valeur == null ? null : valeur.setScale(2, RoundingMode.HALF_UP);
    }
}
