package com.zumm.service.regles;

import com.zumm.domain.Visite;
import com.zumm.repository.VisiteRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * « Nourrir : les reserves sont au plus bas » (SPRINT-22).
 *
 * <p>{@code cadresMiel} existe depuis la V19 et n'alimentait aucune decision. Un
 * cadre de miel ou moins n'est pas une variation saisonniere : c'est le debut
 * d'une famine, et le delai de reaction se compte en jours.
 */
@Component
public class RegleReservesBasses implements RegleTache {

    private static final int SEUIL_CADRES = 1;
    private static final int FENETRE_JOURS = 14;

    private final VisiteRepository visites;

    public RegleReservesBasses(VisiteRepository visites) {
        this.visites = visites;
    }

    @Override
    public String code() {
        return "reserves-basses";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        return visites
                .findByDateVisiteBetweenOrderByDateVisiteAsc(jour.minusDays(FENETRE_JOURS), jour)
                .stream()
                .filter(v -> v.getCadresMiel() != null && v.getCadresMiel() <= SEUIL_CADRES)
                .map(v -> tache(v, jour))
                .toList();
    }

    private TacheProposee tache(Visite v, LocalDate jour) {
        return new TacheProposee(
                code() + ":visite-" + v.getId(),
                "Reserves a " + v.getCadresMiel() + " cadre(s) le " + v.getDateVisite()
                        + " — nourrir la ruche " + v.getRuche().getId(),
                v.getRuche().getId(),
                jour.plusDays(3),
                "critique",
                "nourrissement");
    }
}
