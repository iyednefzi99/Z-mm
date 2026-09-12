package com.zumm.service.regles;

import com.zumm.configmetier.ConfigurationMetier;
import com.zumm.repository.CouvertSolRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Des parcelles attendent une verification terrain (SPRINT-33, lot K).
 *
 * <p>C'est la seconde moitie de la ligne « ground truthing » du §13 : marquer
 * une parcelle « a confirmer » ne sert a rien si personne ne se souvient d'aller
 * voir. BeeGIS conseille la verification ; Zumm la PROGRAMME.
 *
 * <p><strong>Une tache par RUCHER, jamais par parcelle.</strong> Un rucher de
 * plaine touche quarante polygones ; quarante taches « verifier la parcelle
 * 17 843 » rendraient la liste illisible en une matinee, et une liste qu'on
 * n'ouvre plus ne rappelle rien. La tache dit combien de parcelles attendent, et
 * l'ecran de l'environnement les detaille.
 *
 * <p><strong>Une proposition par SAISON.</strong> La cle porte l'annee : le
 * ground truthing est un geste de printemps — on regarde ce qui a leve. Une cle
 * fixe ne reproposerait jamais rien l'annee suivante ; une cle portant le mois
 * reproposerait la meme tournee douze fois par an. Meme arbitrage que
 * {@link RegleStockBas}, a une maille differente parce que le geste l'est.
 */
@Component
public class RegleVerificationCouvert implements RegleTache {

    private final CouvertSolRepository couverts;
    private final ConfigurationMetier configuration;

    public RegleVerificationCouvert(CouvertSolRepository couverts,
            ConfigurationMetier configuration) {
        this.couverts = couverts;
        this.configuration = configuration;
    }

    @Override
    public String code() {
        return "verification-couvert";
    }

    @Override
    public List<TacheProposee> proposer(LocalDate jour) {
        return couverts.ruchersAVerifier(configuration.seuils().rayonButinageKm()).stream()
                .map(rucher -> new TacheProposee(
                        "%s:%d:%d".formatted(code(), rucher.siteId(), jour.getYear()),
                        // Le libelle NOMME le rucher : la tache ne porte pas de
                        // ruche, donc rien d'autre ne la situe a l'ecran.
                        "Verifier au terrain %d parcelle(s) autour de %s".formatted(
                                rucher.parcelles(), rucher.siteNom()),
                        null,
                        jour,
                        // Normale, et pas haute : une couche imprecise fausse une
                        // analyse, elle ne met aucune colonie en danger. Ce qui
                        // est urgent chasse ce qui est important, et l'inverse
                        // aussi.
                        "normale",
                        "controle"))
                .toList();
    }
}
