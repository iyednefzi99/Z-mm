package com.zumm.service;

import com.zumm.domain.Compartiment;
import com.zumm.domain.MesureCompartiment;
import com.zumm.domain.MesureCompartimentId;
import com.zumm.domain.Ruche;
import com.zumm.repository.CompartimentRepository;
import com.zumm.repository.MesureCompartimentRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.MesureCompartimentCorps;
import com.zumm.web.dto.PoidsCompartiment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Poids par compartiment (SPRINT-26, lot F1).
 *
 * <p>Ferme la ligne « poids par hausse » du §5 de
 * {@code docs/ECART-CONCURRENTS.md}. Deux gestes seulement : peser, et lire la
 * répartition d'une ruche.
 *
 * <p><strong>Ce que ce service ne fait pas, et c'est le plus important.</strong>
 * Il ne <em>somme pas</em> les compartiments pour en déduire le poids de la
 * ruche, et il n'alimente ni les alertes de seuil, ni la prévision de récolte,
 * ni la détection d'anomalie. Ces trois-là lisent {@code mesure} — ce qu'une
 * balance pèse sous la ruche entière — et doivent continuer de le faire :
 * additionner des pesées de hausses faites à des moments différents produirait
 * un poids qui n'a jamais existé, et la prévision de récolte compterait deux
 * fois le même miel.
 *
 * <p>La répartition sert à répondre à une autre question, celle qu'un apiculteur
 * se pose devant une ruche lourde : <em>où</em> est le miel.
 */
@Service
@Transactional
public class MesureCompartimentService {

    private final MesureCompartimentRepository mesures;
    private final CompartimentRepository compartiments;
    private final RucheRepository ruches;

    public MesureCompartimentService(MesureCompartimentRepository mesures,
            CompartimentRepository compartiments, RucheRepository ruches) {
        this.mesures = mesures;
        this.compartiments = compartiments;
        this.ruches = ruches;
    }

    /** Enregistre le poids d'un compartiment. */
    public PoidsCompartiment peser(MesureCompartimentCorps corps) {
        Compartiment compartiment = compartiments.findById(corps.compartimentId())
                .orElseThrow(() -> new RequeteInvalide(
                        "Compartiment inconnu dans ce tenant : " + corps.compartimentId()));
        if (corps.valeur().signum() < 0) {
            // Une hausse ne pèse pas moins que rien. La base l'accepterait —
            // `NUMERIC` est signé — et la courbe deviendrait illisible.
            throw new RequeteInvalide("Le poids d'un compartiment ne peut pas être négatif.");
        }
        Instant instant = corps.instant() == null ? Instant.now() : corps.instant();

        MesureCompartiment mesure = mesures.save(new MesureCompartiment(
                new MesureCompartimentId(compartiment.getId(), corps.typeIndicateur(), instant),
                corps.valeur()));
        return new PoidsCompartiment(compartiment.getId(), compartiment.getType().enBase(),
                compartiment.getNbCadres(), mesure.getValeur(), instant);
    }

    /**
     * Répartition du poids d'une ruche, un compartiment par ligne.
     *
     * <p>Les compartiments jamais pesés figurent quand même, avec un poids
     * {@code null}. Les taire donnerait une répartition qui a l'air complète et
     * qui ne l'est pas ; afficher zéro serait pire encore — une hausse jamais
     * pesée n'est pas une hausse vide.
     */
    @Transactional(readOnly = true)
    public List<PoidsCompartiment> repartition(Long rucheId) {
        Ruche ruche = ruches.findById(rucheId)
                .orElseThrow(() -> RessourceIntrouvable.de("Ruche", rucheId));
        List<Compartiment> composition = ruche.getCompartiments();
        if (composition.isEmpty()) {
            return List.of();
        }

        Map<Long, MesureCompartiment> derniers = mesures
                .derniersParCompartiment(composition.stream().map(Compartiment::getId).toList())
                .stream()
                .collect(Collectors.toMap(m -> m.getId().getCompartimentId(),
                        Function.identity(), (premier, second) -> premier));

        return composition.stream()
                .map(c -> ligne(c, derniers.get(c.getId())))
                .toList();
    }

    /** Série d'un compartiment sur une fenêtre, du plus ancien au plus récent. */
    @Transactional(readOnly = true)
    public List<PoidsCompartiment> serie(Long compartimentId, Instant debut, Instant fin) {
        Compartiment compartiment = compartiments.findById(compartimentId)
                .orElseThrow(() -> RessourceIntrouvable.de("Compartiment", compartimentId));
        return mesures
                .findByIdCompartimentIdAndIdInstantBetweenOrderByIdInstantAsc(
                        compartimentId, debut, fin)
                .stream()
                .map(m -> new PoidsCompartiment(compartimentId, compartiment.getType().enBase(),
                        compartiment.getNbCadres(), m.getValeur(), m.getId().getInstant()))
                .toList();
    }

    private PoidsCompartiment ligne(Compartiment compartiment, MesureCompartiment mesure) {
        BigDecimal valeur = mesure == null ? null : mesure.getValeur();
        Instant instant = mesure == null ? null : mesure.getId().getInstant();
        return new PoidsCompartiment(compartiment.getId(), compartiment.getType().enBase(),
                compartiment.getNbCadres(), valeur, instant);
    }
}
