package com.zumm.service.regles;

import com.zumm.domain.Traitement;
import com.zumm.repository.TraitementRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * « Retirer le traitement quand sa carence s'acheve » (SPRINT-22).
 *
 * <p>C'est le cas d'ecole que le §11 du document d'ecart cite depuis le
 * 18/08/2026 : « la regle est mecanique, la valeur immediate — et depuis le
 * SPRINT-20 la donnee est la, {@code traitement.date_retrait} etant en base et
 * indexee. Il ne manque plus que la regle. » La voici.
 *
 * <p>La tache est proposee <strong>trois jours avant</strong> la fin de carence,
 * pas le jour meme : un apiculteur ne se deplace pas a la demande, il organise sa
 * semaine. Une tache qui arrive le matin ou elle est due n'est pas un rappel,
 * c'est un constat de retard.
 */
@Component
public class RegleRetraitTraitement implements RegleTache {

    /** Preavis : de quoi caser la visite dans une semaine deja pleine. */
    private static final int PREAVIS_JOURS = 3;

    private final TraitementRepository traitements;

    public RegleRetraitTraitement(TraitementRepository traitements) {
        this.traitements = traitements;
    }

    @Override
    public String code() {
        return "carence-retrait";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        return traitements.sousCarenceAu(jour).stream()
                .filter(t -> t.getDateRetrait() != null)
                // Uniquement ce qui arrive : un traitement dont la carence court
                // encore trois semaines n'a rien a faire dans la liste du jour.
                .filter(t -> !t.getDateRetrait().isAfter(jour.plusDays(PREAVIS_JOURS)))
                .map(this::tache)
                .toList();
    }

    private TacheProposee tache(Traitement t) {
        return new TacheProposee(
                code() + ":" + t.getId(),
                "Fin de carence le " + t.getDateRetrait() + " — " + t.getProduit()
                        + " (ruche " + t.getRuche().getId() + ")",
                t.getRuche().getId(),
                t.getDateRetrait(),
                // Haute et non critique : la date est connue d'avance, rien ne
                // brule. Reserver « critique » a ce qui se degrade tout seul.
                "haute",
                "traitement");
    }
}
