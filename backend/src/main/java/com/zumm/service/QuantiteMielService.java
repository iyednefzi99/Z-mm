package com.zumm.service;

import com.zumm.repository.RecolteRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.QuantiteMiel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service web tierce {@code getZummHoneyActualQuantity} (US-026, cahier §6.5).
 *
 * <p>Renvoie la quantite de miel recoltee, convertie dans l'unite demandee via
 * {@link ConversionUnites} (US-019). Pour l'ensemble du rucher si aucune ruche
 * n'est precisee.
 *
 * <p><strong>Ce service renvoyait le dernier poids connu de la ruche</strong>, en
 * attendant le module recolte du SPRINT-07. Ce module est livre depuis, et le
 * proxy etait devenu faux sur deux plans : le poids d'une ruche comprend le corps,
 * les cadres et la colonie — donc surestime largement le miel — et il *baisse*
 * apres une recolte, c'est-a-dire au moment ou la quantite recoltee augmente. Un
 * integrateur tiers lisant ce contrat obtenait un nombre qui n'etait pas une masse
 * de miel.
 *
 * <p>La somme est calculee EN BASE, en JPQL : l'implementation precedente
 * chargeait toutes les mesures de poids du tenant en memoire pour n'en garder
 * qu'une par ruche.
 */
@Service
@Transactional(readOnly = true)
public class QuantiteMielService {

    private final RecolteRepository recoltes;
    private final RucheRepository ruches;
    private final ConversionUnites conversion;

    public QuantiteMielService(RecolteRepository recoltes, RucheRepository ruches,
            ConversionUnites conversion) {
        this.recoltes = recoltes;
        this.ruches = ruches;
        this.conversion = conversion;
    }

    public QuantiteMiel getZummHoneyActualQuantity(Long rucheId, String unite) {
        String cible = unite == null || unite.isBlank() ? "kg" : unite;
        BigDecimal enKg = rucheId == null
                ? recoltes.quantiteTotaleRecoltee()
                : rucheKg(rucheId);
        double converti = conversion.convertir(enKg.doubleValue(), "kg", cible);
        return new QuantiteMiel(rucheId, BigDecimal.valueOf(converti).setScale(3, RoundingMode.HALF_UP), cible);
    }

    /**
     * Masse recoltee sur une ruche.
     *
     * <p>L'existence de la ruche est verifiee d'abord : sans cela, une ruche
     * inconnue — ou appartenant a un autre tenant — renverrait « 0 kg », ce qui se
     * lit comme une reponse valide plutot que comme une erreur.
     */
    private BigDecimal rucheKg(Long rucheId) {
        if (ruches.findById(rucheId).isEmpty()) {
            throw new RequeteInvalide("Ruche inconnue dans ce tenant : " + rucheId);
        }
        return recoltes.quantiteRecolteeParRuche(rucheId);
    }
}
