package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.ComptageVarroa;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.ComptageVarroaRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.ComptageVarroaCorps;
import com.zumm.web.dto.ComptageVarroaReponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comptages de varroa et calcul du taux d'infestation (SPRINT-20).
 *
 * <p>Huit des douze catalogues concurrents nomment le varroa ; plusieurs en font
 * un module entier avec calculateur. Le depot ne le nommait nulle part, sauf
 * dans la prose du jeu de demonstration.
 *
 * <p><strong>Le taux est calcule ici, pas stocke.</strong> C'est le meme
 * arbitrage que la regle des 100 % de {@code LotConditionnementService} : une
 * valeur qu'on doit savoir expliquer a l'utilisateur ne se cache pas dans une
 * colonne generee. Et surtout, elle n'a pas la meme unite selon la methode — un
 * champ unique melangeant chute journaliere et pourcentage serait faux, et les
 * seuils qu'on en tirerait le seraient aussi.
 */
@Service
@Transactional
public class ComptageVarroaService {

    /**
     * Seuils de decision, par unite.
     *
     * <p>Ils suivent les reperes communement admis en apiculture : au-dela de
     * trois varroas pour cent abeilles, ou d'une chute naturelle de cinq varroas
     * par jour en saison, un traitement s'impose. Ce sont des ordres de
     * grandeur, pas une norme : ils sont ici pour DECLENCHER une lecture, jamais
     * pour tenir lieu de diagnostic — d'ou un verdict en trois mots plutot qu'un
     * feu rouge.
     */
    private static final BigDecimal SEUIL_TRAITER_POUR_CENT = new BigDecimal("3");
    private static final BigDecimal SEUIL_SURVEILLER_POUR_CENT = new BigDecimal("1");
    private static final BigDecimal SEUIL_TRAITER_PAR_JOUR = new BigDecimal("5");
    private static final BigDecimal SEUIL_SURVEILLER_PAR_JOUR = new BigDecimal("2");

    private final ComptageVarroaRepository comptages;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final VisiteRepository visites;

    public ComptageVarroaService(ComptageVarroaRepository comptages, RucheRepository ruches,
            AgentRepository agents, VisiteRepository visites) {
        this.comptages = comptages;
        this.ruches = ruches;
        this.agents = agents;
        this.visites = visites;
    }

    public ComptageVarroaReponse enregistrer(ComptageVarroaCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        Agent agent = agents.findById(corps.agentId()).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + corps.agentId()));

        ComptageVarroa c = new ComptageVarroa(ruche, agent, corps.dateComptage(),
                corps.methode(), corps.varroasComptes());
        c.setAbeillesEchantillon(corps.abeillesEchantillon());
        c.setJoursExposition(corps.joursExposition());
        c.setNote(corps.note());
        c.setVisite(visiteRattachee(corps.visiteId()));

        verifierDenominateur(c);

        return rendre(comptages.save(c));
    }

    /** Serie d'une ruche, du comptage le plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<ComptageVarroaReponse> serie(Long rucheId) {
        if (ruches.findById(rucheId).isEmpty()) {
            throw RessourceIntrouvable.de("Ruche", rucheId);
        }
        return comptages.findByRuche_IdOrderByDateComptageDescIdDesc(rucheId).stream()
                .map(this::rendre).toList();
    }

    public void supprimer(Long id) {
        ComptageVarroa c = comptages.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Comptage de varroa", id));
        comptages.delete(c);
    }

    /**
     * Taux d'infestation, dans l'unite de la methode.
     *
     * <p>Lange : varroas tombes <strong>par jour</strong>. Toute autre methode :
     * varroas <strong>pour cent abeilles</strong>. L'unite accompagne la valeur
     * jusqu'a l'ecran ({@code ComptageVarroaReponse.tauxUnite}) — un « 3,5 » nu
     * ne veut rien dire.
     */
    public static BigDecimal taux(ComptageVarroa c) {
        BigDecimal comptes = BigDecimal.valueOf(c.getVarroasComptes());
        if (c.parLange()) {
            return c.getJoursExposition() == null ? null
                    : comptes.divide(BigDecimal.valueOf(c.getJoursExposition()), 2,
                            RoundingMode.HALF_UP);
        }
        return c.getAbeillesEchantillon() == null ? null
                : comptes.multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(c.getAbeillesEchantillon()), 2,
                                RoundingMode.HALF_UP);
    }

    /**
     * Lecture du taux contre les seuils de sa methode.
     *
     * <p>{@code inconnu} quand le denominateur manque : dire « faible » faute de
     * pouvoir calculer serait la pire des reponses — elle rassure sans rien
     * savoir.
     */
    public static String verdict(ComptageVarroa c) {
        BigDecimal t = taux(c);
        if (t == null) {
            return "inconnu";
        }
        BigDecimal traiter = c.parLange() ? SEUIL_TRAITER_PAR_JOUR : SEUIL_TRAITER_POUR_CENT;
        BigDecimal surveiller =
                c.parLange() ? SEUIL_SURVEILLER_PAR_JOUR : SEUIL_SURVEILLER_POUR_CENT;
        if (t.compareTo(traiter) >= 0) {
            return "traiter";
        }
        return t.compareTo(surveiller) >= 0 ? "surveiller" : "faible";
    }

    private ComptageVarroaReponse rendre(ComptageVarroa c) {
        return ComptageVarroaReponse.de(c, taux(c), verdict(c));
    }

    private Visite visiteRattachee(Long visiteId) {
        if (visiteId == null) {
            return null;
        }
        return visites.findById(visiteId).orElseThrow(() ->
                new RequeteInvalide("Visite inconnue dans ce tenant : " + visiteId));
    }

    /**
     * Chaque methode exige SON denominateur, et refuse celui de l'autre.
     *
     * <p>Sans lui la ligne est incalculable, donc inutile : « 40 varroas » est
     * anodin sur trois jours de lange et alarmant sur 300 abeilles. La base
     * porte la meme regle en CHECK ; ici elle produit un 400 lisible plutot
     * qu'une erreur SQL en 500.
     */
    private void verifierDenominateur(ComptageVarroa c) {
        if (c.estValide()) {
            return;
        }
        throw new RequeteInvalide(c.parLange()
                ? "Un comptage par lange se rapporte a une duree : renseigner joursExposition, "
                        + "et pas abeillesEchantillon."
                : "Un comptage par echantillon se rapporte a un nombre d'abeilles : renseigner "
                        + "abeillesEchantillon, et pas joursExposition.");
    }
}
