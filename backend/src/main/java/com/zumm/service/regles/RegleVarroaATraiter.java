package com.zumm.service.regles;

import com.zumm.domain.ComptageVarroa;
import com.zumm.repository.ComptageVarroaRepository;
import com.zumm.service.ComptageVarroaService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * « Traiter, le comptage le demande » (SPRINT-22).
 *
 * <p>Le SPRINT-20 calcule le taux d'infestation et rend un verdict ; personne
 * n'en faisait rien. Cette regle transforme le verdict {@code traiter} en tache.
 *
 * <p><strong>Elle lit le verdict, jamais le taux.</strong> Le taux n'a pas la
 * meme unite selon la methode — varroas par jour pour un lange, pour-cent
 * d'abeilles pour un echantillon — et {@code ComptageVarroaService} porte les
 * deux jeux de seuils. Comparer un taux brut a un seuil unique se tromperait
 * d'un facteur dix une fois sur deux.
 */
@Component
public class RegleVarroaATraiter implements RegleTache {

    /** Un comptage plus vieux que cela ne dit plus l'infestation d'aujourd'hui. */
    private static final int FRAICHEUR_JOURS = 30;

    private final ComptageVarroaRepository comptages;

    public RegleVarroaATraiter(ComptageVarroaRepository comptages) {
        this.comptages = comptages;
    }

    @Override
    public String code() {
        return "varroa-traiter";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        return comptages.findAll().stream()
                .filter(c -> !c.getDateComptage().isBefore(jour.minusDays(FRAICHEUR_JOURS)))
                .filter(c -> "traiter".equals(ComptageVarroaService.verdict(c)))
                .map(c -> tache(c, jour))
                .toList();
    }

    private TacheProposee tache(ComptageVarroa c, LocalDate jour) {
        return new TacheProposee(
                // La cle porte le COMPTAGE et non la ruche : deux comptages
                // successifs au-dessus du seuil sont deux alertes distinctes, et
                // la seconde ne doit pas etre etouffee par la premiere.
                code() + ":" + c.getId(),
                "Infestation au-dessus du seuil (comptage du " + c.getDateComptage()
                        + ") — traiter la ruche " + c.getRuche().getId(),
                c.getRuche().getId(),
                jour.plusDays(2),
                "critique",
                "traitement");
    }
}
