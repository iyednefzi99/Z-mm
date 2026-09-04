package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Nourrissement;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.NourrissementRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.NourrissementCorps;
import com.zumm.web.dto.NourrissementLotCorps;
import com.zumm.web.dto.RapportLot;
import com.zumm.web.dto.NourrissementReponse;
import java.util.List;
import java.util.Set;
import com.zumm.domain.Ruche;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registre des nourrissements (SPRINT-20).
 *
 * <p>Deuxieme acte que {@code RaisonVisite.NOURRISSAGE} ne savait que nommer.
 */
@Service
@Transactional
public class NourrissementService {

    /** Alimentation liquide : elle se mesure en volume, jamais en masse. */
    private static final Set<String> LIQUIDES =
            Set.of("sirop_1_1", "sirop_2_1", "eau", "miel");

    /** Alimentation solide : elle se mesure en masse, jamais en volume. */
    private static final Set<String> SOLIDES =
            Set.of("candi", "pollen", "substitut_pollen");

    private final NourrissementRepository nourrissements;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final VisiteRepository visites;
    private final OperationsLotService lots;

    public NourrissementService(NourrissementRepository nourrissements, RucheRepository ruches,
            AgentRepository agents, VisiteRepository visites, OperationsLotService lots) {
        this.nourrissements = nourrissements;
        this.ruches = ruches;
        this.agents = agents;
        this.visites = visites;
        this.lots = lots;
    }

    /**
     * Nourrit plusieurs ruches d'un coup (SPRINT-23, lot B).
     *
     * <p>C'est l'operation qui, en pratique, se saisit le plus souvent en lot :
     * le sirop se pose rucher par rucher, jamais colonie par colonie.
     */
    public RapportLot enregistrerLot(NourrissementLotCorps corps) {
        return lots.executer(corps.cible(), ruche -> enregistrerPour(ruche, corps.nourrissement()));
    }

    /** Un nourrissement pour UNE ruche, dans sa propre transaction (echec partiel). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enregistrerPour(Ruche ruche, NourrissementCorps modele) {
        NourrissementCorps pourCetteRuche = new NourrissementCorps(
                ruche.getId(), modele.agentId(), modele.visiteId(), modele.dateApport(),
                modele.typeAliment(), modele.quantite(), modele.quantiteUnite(), modele.motif(),
                modele.note());
        return enregistrer(pourCetteRuche).id();
    }

    public NourrissementReponse enregistrer(NourrissementCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        Agent agent = agents.findById(corps.agentId()).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + corps.agentId()));

        verifierUnite(corps);

        Nourrissement n = new Nourrissement(ruche, agent, corps.dateApport(),
                corps.typeAliment(), corps.quantite(), corps.quantiteUnite());
        n.setMotif(corps.motif());
        n.setNote(corps.note());
        n.setVisite(visiteRattachee(corps.visiteId()));

        return NourrissementReponse.de(nourrissements.save(n));
    }

    /** Apports d'une ruche, du plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<NourrissementReponse> registre(Long rucheId) {
        if (ruches.findById(rucheId).isEmpty()) {
            throw RessourceIntrouvable.de("Ruche", rucheId);
        }
        return nourrissements.findByRuche_IdOrderByDateApportDescIdDesc(rucheId).stream()
                .map(NourrissementReponse::de).toList();
    }

    public void supprimer(Long id) {
        Nourrissement n = nourrissements.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Nourrissement", id));
        nourrissements.delete(n);
    }

    private Visite visiteRattachee(Long visiteId) {
        if (visiteId == null) {
            return null;
        }
        return visites.findById(visiteId).orElseThrow(() ->
                new RequeteInvalide("Visite inconnue dans ce tenant : " + visiteId));
    }

    /**
     * L'unite doit correspondre a l'etat de l'aliment.
     *
     * <p>« 3 kg de sirop » et « 3 litres de candi » sont des saisies plausibles
     * au clavier et absurdes au rucher. La base ne peut pas l'attraper — son
     * CHECK porte sur chaque colonne separement, pas sur leur accord — et un
     * bilan de saison construit sur des unites melangees est faux sans jamais
     * paraitre l'etre. C'est exactement le genre de regle qui appartient au
     * service : elle a un message a expliquer.
     */
    private void verifierUnite(NourrissementCorps corps) {
        String type = corps.typeAliment();
        String unite = corps.quantiteUnite();
        boolean volume = "l".equals(unite) || "ml".equals(unite);

        if (LIQUIDES.contains(type) && !volume) {
            throw new RequeteInvalide(
                    "Un apport liquide (" + type + ") se mesure en l ou ml, pas en " + unite + ".");
        }
        if (SOLIDES.contains(type) && volume) {
            throw new RequeteInvalide(
                    "Un apport solide (" + type + ") se mesure en kg ou g, pas en " + unite + ".");
        }
    }
}
