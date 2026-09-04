package com.zumm.service.regles;

import com.zumm.domain.Visite;
import com.zumm.repository.VisiteRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * « Revenir voir la ponte a J+7 » (SPRINT-22).
 *
 * <p>Deux constats de visite appellent le meme geste, et c'est pour cela qu'ils
 * partagent une regle : une colonie sans couvain operculé et sans reine vue peut
 * etre orpheline, et une colonie qui porte des cellules royales aura tranche son
 * sort en une semaine. Dans les deux cas, la reponse n'est pas d'agir tout de
 * suite — c'est de <strong>revenir voir</strong>.
 *
 * <p>Sept jours : le delai au terme duquel une reine vierge devrait avoir ete
 * fecondee et commence a pondre. Plus tot, on ne verrait rien ; plus tard, on
 * perd une semaine de saison.
 */
@Component
public class RegleControlePonte implements RegleTache {

    private static final int DELAI_JOURS = 7;

    /** Au-dela, la visite est trop ancienne pour qu'un controle « a J+7 » ait un sens. */
    private static final int FENETRE_JOURS = 21;

    private final VisiteRepository visites;

    public RegleControlePonte(VisiteRepository visites) {
        this.visites = visites;
    }

    @Override
    public String code() {
        return "controle-ponte";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        return visites
                .findByDateVisiteBetweenOrderByDateVisiteAsc(jour.minusDays(FENETRE_JOURS), jour)
                .stream()
                .filter(this::appelleUnControle)
                .map(v -> new TacheProposee(
                        code() + ":visite-" + v.getId(),
                        motif(v) + " — controler la ponte (ruche " + v.getRuche().getId() + ")",
                        v.getRuche().getId(),
                        v.getDateVisite().plusDays(DELAI_JOURS),
                        "haute",
                        "controle"))
                .toList();
    }

    private boolean appelleUnControle(Visite v) {
        return orphelinagePossible(v) || cellulesRoyales(v);
    }

    /**
     * Orphelinage POSSIBLE, et non certain : c'est le couple qui compte.
     *
     * <p>Une reine non vue seule ne dit rien — elle se cache, et on ne la cherche
     * pas a chaque visite. Un couvain opercule absent seul s'explique par une
     * ruche fraichement peuplee. Les deux ensemble justifient de revenir.
     */
    private boolean orphelinagePossible(Visite v) {
        return Boolean.FALSE.equals(v.getCouvainOpercule()) && Boolean.FALSE.equals(v.getReineVue());
    }

    private boolean cellulesRoyales(Visite v) {
        return v.getCellulesRoyales() != null && v.getCellulesRoyales() > 0;
    }

    private String motif(Visite v) {
        return cellulesRoyales(v) ? "Cellules royales le " + v.getDateVisite()
                : "Couvain absent et reine non vue le " + v.getDateVisite();
    }
}
