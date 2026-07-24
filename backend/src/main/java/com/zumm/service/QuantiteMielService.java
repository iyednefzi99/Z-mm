package com.zumm.service;

import com.zumm.domain.Mesure;
import com.zumm.domain.TypeIndicateur;
import com.zumm.repository.MesureRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.QuantiteMiel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service web tierce {@code getZummHoneyActualQuantity} (US-026, cahier §6.5).
 *
 * <p>Renvoie la quantite de miel actuelle, estimee a partir du dernier poids connu
 * de la ruche (proxy en attendant le module recolte, SPRINT-07), convertie dans
 * l'unite demandee via {@link ConversionUnites} (US-019). Pour l'ensemble du
 * rucher si aucune ruche n'est precisee.
 */
@Service
@Transactional(readOnly = true)
public class QuantiteMielService {

    private final MesureRepository mesures;
    private final RucheRepository ruches;
    private final ConversionUnites conversion;

    public QuantiteMielService(MesureRepository mesures, RucheRepository ruches,
            ConversionUnites conversion) {
        this.mesures = mesures;
        this.ruches = ruches;
        this.conversion = conversion;
    }

    public QuantiteMiel getZummHoneyActualQuantity(Long rucheId, String unite) {
        String cible = unite == null || unite.isBlank() ? "kg" : unite;
        BigDecimal enKg = rucheId == null ? totalKg() : rucheKg(rucheId);
        double converti = conversion.convertir(enKg.doubleValue(), "kg", cible);
        return new QuantiteMiel(rucheId, BigDecimal.valueOf(converti).setScale(3, RoundingMode.HALF_UP), cible);
    }

    private BigDecimal rucheKg(Long rucheId) {
        if (ruches.findById(rucheId).isEmpty()) {
            throw new RequeteInvalide("Ruche inconnue dans ce tenant : " + rucheId);
        }
        var serie = mesures.findByIdRucheIdAndIdTypeIndicateurOrderByIdInstantAsc(rucheId, TypeIndicateur.POIDS);
        return serie.isEmpty() ? BigDecimal.ZERO : serie.get(serie.size() - 1).getValeur();
    }

    private BigDecimal totalKg() {
        Map<Long, BigDecimal> dernierParRuche = new LinkedHashMap<>();
        for (Mesure m : mesures.findByIdTypeIndicateurOrderByIdRucheIdAscIdInstantAsc(TypeIndicateur.POIDS)) {
            dernierParRuche.put(m.getId().getRucheId(), m.getValeur());
        }
        return dernierParRuche.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
