package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Division;
import com.zumm.domain.Ruche;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.DivisionRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.DivisionCorps;
import com.zumm.web.dto.DivisionReponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registre des divisions (SPRINT-21).
 *
 * <p>Repond au 🟡 du §1 de {@code docs/ECART-CONCURRENTS.md} : le depot savait
 * qu'une division avait eu lieu ({@code RaisonVisite.DIVISION},
 * {@code EtatRuche.EN_DIVISION}) sans jamais savoir de quelle mere venait quelle
 * fille. HiveBook en fait une entite ; c'en est une ici aussi.
 */
@Service
@Transactional
public class DivisionService {

    private final DivisionRepository divisions;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final VisiteRepository visites;

    public DivisionService(DivisionRepository divisions, RucheRepository ruches,
            AgentRepository agents, VisiteRepository visites) {
        this.divisions = divisions;
        this.ruches = ruches;
        this.agents = agents;
        this.visites = visites;
    }

    public DivisionReponse enregistrer(DivisionCorps corps) {
        Ruche mere = rucheRequise(corps.rucheMereId());
        Agent agent = agents.findById(corps.agentId()).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + corps.agentId()));

        Division division = new Division(mere, agent, corps.dateDivision());
        division.setMethode(corps.methode());
        division.setCadresCouvain(corps.cadresCouvain());
        division.setCadresProvisions(corps.cadresProvisions());
        division.setOrigineReine(corps.origineReine());
        division.setNote(corps.note());
        division.setVisite(visiteRattachee(corps.visiteId()));

        if (corps.rucheFilleId() != null) {
            division.setFille(filleValide(corps.rucheMereId(), corps.rucheFilleId()));
        }
        return DivisionReponse.de(divisions.save(division));
    }

    /** Divisions issues d'une ruche, de la plus recente a la plus ancienne. */
    @Transactional(readOnly = true)
    public List<DivisionReponse> registre(Long rucheMereId) {
        rucheExistante(rucheMereId);
        return divisions.findByMere_IdOrderByDateDivisionDescIdDesc(rucheMereId).stream()
                .map(DivisionReponse::de).toList();
    }

    /**
     * Filiation d'une ruche dans les deux sens : ce dont elle est issue et ce
     * qu'elle a engendre.
     *
     * <p>Les deux sens ensemble et non deux routes : sur l'ecran d'une ruche, la
     * question « d'ou vient-elle » et la question « qu'a-t-elle donne » se posent
     * au meme moment, et se repondent avec la meme table.
     */
    @Transactional(readOnly = true)
    public List<DivisionReponse> filiation(Long rucheId) {
        rucheExistante(rucheId);
        return divisions.filiation(rucheId).stream().map(DivisionReponse::de).toList();
    }

    public void supprimer(Long id) {
        Division division = divisions.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Division", id));
        divisions.delete(division);
    }

    /**
     * Verifie qu'une fille est utilisable, et lui pose son origine.
     *
     * <p>Deux refus, tous deux traduits en 400 plutot qu'en violation de
     * contrainte : une ruche ne se divise pas vers elle-meme, et une ruche n'a
     * qu'une seule mere — {@code uq_division_fille} le garantit en base, mais une
     * erreur SQL en 500 n'apprend rien a l'apiculteur.
     *
     * <p>L'origine de la fille est ALIGNEE ici ({@code 'division'}) quand elle
     * n'est pas deja renseignee : la filiation et l'origine disent la meme chose,
     * et les laisser diverger ferait mentir une statistique par origine que le
     * SPRINT-20 venait de rendre possible.
     */
    private Ruche filleValide(Long mereId, Long filleId) {
        if (filleId.equals(mereId)) {
            throw new RequeteInvalide("Une ruche ne peut pas etre sa propre fille.");
        }
        Ruche fille = rucheRequise(filleId);
        divisions.findByFille_Id(filleId).ifPresent(existante -> {
            throw new RequeteInvalide("La ruche " + filleId + " est deja issue de la division "
                    + existante.getId() + ".");
        });
        if (fille.getOrigine() == null) {
            fille.setOrigine("division");
        }
        return fille;
    }

    private Ruche rucheRequise(Long id) {
        return ruches.findById(id).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + id));
    }

    private void rucheExistante(Long id) {
        if (ruches.findById(id).isEmpty()) {
            throw RessourceIntrouvable.de("Ruche", id);
        }
    }

    private Visite visiteRattachee(Long visiteId) {
        if (visiteId == null) {
            return null;
        }
        return visites.findById(visiteId).orElseThrow(() ->
                new RequeteInvalide("Visite inconnue dans ce tenant : " + visiteId));
    }
}
