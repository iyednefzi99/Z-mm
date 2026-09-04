package com.zumm.service.regles;

import com.zumm.domain.Consommable;
import com.zumm.repository.ConsommableRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Un consommable est passe sous son seuil (SPRINT-27, lot E).
 *
 * <p>C'est ce qui rend le stock utile : sans cette regle, la table dit ce qu'on
 * a et jamais ce qui manque — un inventaire, pas un stock.
 *
 * <p><strong>Une proposition par MOIS et par consommable.</strong> La cle porte
 * l'annee et le mois, et ce choix est le coeur de la regle : une cle fixe ne
 * reproposerait jamais rien apres la premiere fois, meme un an plus tard ; une
 * cle portant le jour reproposerait la meme tache chaque matin, et la liste
 * deviendrait illisible en une semaine. Le mois est la maille d'une commande de
 * consommables.
 */
@Component
public class RegleStockBas implements RegleTache {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ConsommableRepository consommables;

    public RegleStockBas(ConsommableRepository consommables) {
        this.consommables = consommables;
    }

    @Override
    public String code() {
        return "stock-bas";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        return consommables.findAll().stream()
                .filter(Consommable::sousSeuil)
                .map(c -> new TacheProposee(
                        "%s:%d:%s".formatted(code(), c.getId(), MOIS.format(jour)),
                        "Reapprovisionner : %s (%s %s en stock, seuil %s)".formatted(
                                c.getLibelle(), c.getQuantite(), c.getUnite(), c.getSeuilAlerte()),
                        null,
                        jour,
                        "normale",
                        "materiel"))
                .toList();
    }
}
