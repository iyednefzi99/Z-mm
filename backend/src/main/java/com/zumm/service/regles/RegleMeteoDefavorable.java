package com.zumm.service.regles;

import com.zumm.domain.Planning;
import com.zumm.domain.StatutPlanning;
import com.zumm.repository.PlanningRepository;
import com.zumm.service.MeteoService;
import com.zumm.web.dto.PrevisionJour;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * « Cette visite tombe un jour de pluie » (SPRINT-22).
 *
 * <p>Repond a la ligne « taches programmees selon la meteo » du §7 de
 * {@code docs/ECART-CONCURRENTS.md}. Trois concurrents proposent d'adapter le
 * planning au temps ; Zumm avait la prevision depuis le SPRINT-13 et n'en tirait
 * aucune consequence.
 *
 * <p><strong>Elle ne deplace rien.</strong> La regle propose de REPLANIFIER,
 * elle ne touche pas au planning : deplacer d'office la visite d'un agent —
 * peut-etre deja en route, peut-etre le seul creneau de sa semaine — serait
 * decider a sa place sur la foi d'une prevision a cinq jours.
 *
 * <p><strong>Elle echoue en silence.</strong> Le fournisseur meteo est un service
 * tiers : indisponible, il ne doit pas faire echouer l'execution des quatre
 * autres regles. Une prevision manquante vaut « pas d'avis », jamais « beau
 * temps ».
 */
@Component
public class RegleMeteoDefavorable implements RegleTache {

    private static final Logger LOG = LoggerFactory.getLogger(RegleMeteoDefavorable.class);

    /** Horizon : au-dela, la prevision ne vaut pas qu'on bouscule un planning. */
    private static final int HORIZON_JOURS = 5;

    /** Une colonie ouverte sous la pluie se refroidit ; le couvain en patit. */
    private static final double PLUIE_MM = 5.0;

    /** Au-dela, les abeilles deviennent agressives et les cadres se manipulent mal. */
    private static final double VENT_KMH = 40.0;

    /** En dessous, on n'ouvre pas : le couvain se refroidit en quelques minutes. */
    private static final double TEMPERATURE_MIN = 12.0;

    private final PlanningRepository plannings;
    private final MeteoService meteo;

    public RegleMeteoDefavorable(PlanningRepository plannings, MeteoService meteo) {
        this.plannings = plannings;
        this.meteo = meteo;
    }

    @Override
    public String code() {
        return "meteo-defavorable";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        List<Planning> prevus =
                plannings.parPeriode(jour, jour.plusDays(HORIZON_JOURS), StatutPlanning.REFUSE);
        if (prevus.isEmpty()) {
            return List.of();
        }

        // Une seule interrogation par SITE, et non par planning : quarante ruches
        // d'un meme rucher partagent la meme meteo, et le fournisseur tiers n'a
        // pas a etre appele quarante fois.
        Map<Long, List<PrevisionJour>> parSite = new HashMap<>();
        List<TacheProposee> proposees = new ArrayList<>();

        for (Planning planning : prevus) {
            if (planning.getRuche().getSite() == null) {
                continue;
            }
            Long siteId = planning.getRuche().getSite().getId();
            List<PrevisionJour> previsions = parSite.computeIfAbsent(siteId, this::previsions);
            defavorable(previsions, planning.getDatePrevue())
                    .ifPresent(motif -> proposees.add(tache(planning, motif)));
        }
        return proposees;
    }

    private List<PrevisionJour> previsions(Long siteId) {
        try {
            return meteo.pourSite(siteId, HORIZON_JOURS).previsions();
        } catch (RuntimeException echec) {
            // Pas d'avis plutot qu'un mauvais avis : sans prevision, la regle se
            // tait, et les quatre autres continuent de s'executer.
            LOG.debug("Meteo indisponible pour le site {} : {}", siteId, echec.getMessage());
            return List.of();
        }
    }

    /** Motif du report, ou vide si la journee est praticable. */
    private Optional<String> defavorable(List<PrevisionJour> previsions, LocalDate date) {
        return previsions.stream()
                .filter(p -> date.equals(p.date()))
                .findFirst()
                .flatMap(p -> {
                    if (p.precipitationsMm() != null && p.precipitationsMm() >= PLUIE_MM) {
                        return Optional.of("pluie annoncee (" + p.precipitationsMm() + " mm)");
                    }
                    if (p.ventMaxKmh() != null && p.ventMaxKmh() >= VENT_KMH) {
                        return Optional.of("vent annonce (" + p.ventMaxKmh() + " km/h)");
                    }
                    if (p.temperatureMaxCelsius() != null
                            && p.temperatureMaxCelsius() < TEMPERATURE_MIN) {
                        return Optional.of("temperature trop basse ("
                                + p.temperatureMaxCelsius() + " °C)");
                    }
                    return Optional.empty();
                });
    }

    private TacheProposee tache(Planning planning, String motif) {
        return new TacheProposee(
                code() + ":planning-" + planning.getId(),
                "Visite du " + planning.getDatePrevue() + " compromise — " + motif
                        + " (ruche " + planning.getRuche().getId() + ")",
                planning.getRuche().getId(),
                planning.getDatePrevue(),
                // Ni critique ni haute : rien ne se degrade si l'on ne fait rien.
                // On perd une matinee, pas une colonie.
                "normale",
                "controle");
    }
}
