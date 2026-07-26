package com.zumm.service;

import com.zumm.domain.Visite;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.dto.CalendrierCellule;
import com.zumm.web.dto.CalendrierCellule.VisiteBreve;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendrier matriciel agents x ruches (US-012).
 *
 * <p>Issu de la scission de l'ancien {@code TableauDeBordService}, qui portait
 * trois tableaux de bord — donc trois raisons de changer. Celui-ci n'en a plus
 * qu'une : la maniere de presenter les visites dans le temps.
 *
 * <p>Le regroupement reste EN MEMOIRE, et c'est justifie : la requete est bornee
 * par une PERIODE, ce qui limite naturellement le volume. C'est la difference avec
 * la production et les alertes, qui balayaient l'historique entier et ont, elles,
 * ete poussees en SQL au SPRINT-17.
 */
@Service
@Transactional(readOnly = true)
public class CalendrierService {

    private final VisiteRepository visites;

    public CalendrierService(VisiteRepository visites) {
        this.visites = visites;
    }

    /** Cellules (agent x ruche) des visites tombant dans [debut, fin]. */
    public List<CalendrierCellule> calendrier(LocalDate debut, LocalDate fin) {
        Map<List<Long>, List<Visite>> parCouple = new LinkedHashMap<>();
        for (Visite v : visites.findByDateVisiteBetweenOrderByDateVisiteAsc(debut, fin)) {
            parCouple.computeIfAbsent(List.of(v.getAgent().getId(), v.getRuche().getId()),
                    cle -> new ArrayList<>()).add(v);
        }
        List<CalendrierCellule> cellules = new ArrayList<>();
        for (List<Visite> groupe : parCouple.values()) {
            Visite ref = groupe.get(0);
            cellules.add(new CalendrierCellule(
                    ref.getAgent().getId(), ref.getAgent().getNom(),
                    ref.getRuche().getId(), ref.getRuche().getModele(),
                    groupe.size(), groupe.stream().map(VisiteBreve::de).toList()));
        }
        return cellules;
    }
}
