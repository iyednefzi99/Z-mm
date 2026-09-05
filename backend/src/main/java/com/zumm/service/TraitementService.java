package com.zumm.service;

import com.zumm.domain.Agent;
import com.zumm.domain.Ruche;
import com.zumm.domain.Traitement;
import com.zumm.domain.Visite;
import com.zumm.repository.AgentRepository;
import com.zumm.repository.RucheRepository;
import com.zumm.repository.TraitementRepository;
import com.zumm.repository.VisiteRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.RessourceIntrouvable;
import com.zumm.web.dto.RapportLot;
import com.zumm.web.dto.TraitementCorps;
import com.zumm.web.dto.TraitementLotCorps;
import com.zumm.web.dto.TraitementReponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registre des traitements sanitaires (SPRINT-20).
 *
 * <p>Repond au premier ecart de {@code docs/ECART-CONCURRENTS.md} : jusqu'ici,
 * {@code RaisonVisite.TRAITEMENT} disait qu'on avait traite sans jamais dire
 * avec quoi, a quelle dose, ni sous quel delai de carence — ce qui rendait tout
 * registre sanitaire opposable impossible.
 *
 * <p>La regle de carence depend du jour courant. Elle n'est pour autant pas
 * ecrite ici : {@link Traitement#sousCarence(LocalDate)} la porte et prend la
 * date en parametre, ce qui la rend testable sans horloge injectee — le service
 * se contente de lui passer {@code LocalDate.now()}, comme le reste du depot.
 */
@Service
@Transactional
public class TraitementService {

    private final TraitementRepository traitements;
    private final RucheRepository ruches;
    private final AgentRepository agents;
    private final VisiteRepository visites;
    private final OperationsLotService lots;

    public TraitementService(TraitementRepository traitements, RucheRepository ruches,
            AgentRepository agents, VisiteRepository visites, OperationsLotService lots) {
        this.traitements = traitements;
        this.ruches = ruches;
        this.agents = agents;
        this.visites = visites;
        this.lots = lots;
    }

    public TraitementReponse enregistrer(TraitementCorps corps) {
        Ruche ruche = ruches.findById(corps.rucheId()).orElseThrow(() ->
                new RequeteInvalide("Ruche inconnue dans ce tenant : " + corps.rucheId()));
        Agent agent = agents.findById(corps.agentId()).orElseThrow(() ->
                new RequeteInvalide("Agent inconnu dans ce tenant : " + corps.agentId()));

        verifierPeriode(corps);
        verifierDose(corps);

        Traitement t = new Traitement(ruche, agent, corps.produit().trim(),
                corps.cible(), corps.dateDebut());
        t.setSubstanceActive(corps.substanceActive());
        t.setDose(corps.dose());
        t.setDoseUnite(corps.doseUnite());
        t.setDateFin(corps.dateFin());
        t.setDelaiCarenceJours(corps.delaiCarenceJours());
        t.setOrdonnance(corps.ordonnance());
        t.setOrdonnanceVeterinaire(corps.ordonnanceVeterinaire());
        t.setOrdonnanceDate(corps.ordonnanceDate());
        t.setNote(corps.note());
        t.setVisite(visiteRattachee(corps.visiteId()));

        return TraitementReponse.de(traitements.save(t), LocalDate.now());
    }

    /**
     * Applique le meme traitement a plusieurs ruches (SPRINT-23, lot B).
     *
     * <p>Chaque ruche a sa propre transaction : une colonie clôturee dans le lot
     * ne doit pas annuler les trente-sept traitements qui ont bien eu lieu.
     * L'appel unitaire reste le meme code — c'est {@link #enregistrer} qui est
     * rejoue par ruche, et non une seconde implementation qui finirait par
     * diverger.
     */
    public RapportLot enregistrerLot(TraitementLotCorps corps) {
        return lots.executer(corps.cible(), ruche -> enregistrerPour(ruche, corps.traitement()));
    }

    /**
     * Un traitement pour UNE ruche, dans sa propre transaction.
     *
     * <p>{@code REQUIRES_NEW} est ce qui rend l'echec partiel possible : sans
     * cela, le refus de la trente-huitieme ruche marquerait la transaction
     * englobante pour annulation, et les trente-sept precedentes seraient
     * perdues au commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long enregistrerPour(Ruche ruche, TraitementCorps modele) {
        TraitementCorps pourCetteRuche = new TraitementCorps(
                ruche.getId(), modele.agentId(), modele.visiteId(), modele.produit(),
                modele.substanceActive(), modele.cible(), modele.dose(), modele.doseUnite(),
                modele.dateDebut(), modele.dateFin(), modele.delaiCarenceJours(),
                modele.ordonnance(), modele.ordonnanceVeterinaire(), modele.ordonnanceDate(),
                modele.note());
        return enregistrer(pourCetteRuche).id();
    }

    /** Registre d'une ruche, du traitement le plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<TraitementReponse> registre(Long rucheId) {
        if (ruches.findById(rucheId).isEmpty()) {
            throw RessourceIntrouvable.de("Ruche", rucheId);
        }
        LocalDate jour = LocalDate.now();
        return traitements.findByRuche_IdOrderByDateDebutDescIdDesc(rucheId).stream()
                .map(t -> TraitementReponse.de(t, jour)).toList();
    }

    /**
     * Traitements dont la carence court encore.
     *
     * <p>C'est la reponse a « que puis-je recolter aujourd'hui ». Elle est
     * exposee telle quelle plutot que reduite a une liste d'identifiants de
     * ruches : l'apiculteur a besoin de savoir POURQUOI une ruche est bloquee et
     * jusqu'a quand, sinon il contourne.
     */
    @Transactional(readOnly = true)
    public List<TraitementReponse> sousCarence() {
        LocalDate jour = LocalDate.now();
        return traitements.sousCarenceAu(jour).stream()
                .filter(t -> t.sousCarence(jour))
                .map(t -> TraitementReponse.de(t, jour))
                .toList();
    }

    public void supprimer(Long id) {
        Traitement t = traitements.findById(id)
                .orElseThrow(() -> RessourceIntrouvable.de("Traitement", id));
        traitements.delete(t);
    }

    private Visite visiteRattachee(Long visiteId) {
        if (visiteId == null) {
            return null;
        }
        return visites.findById(visiteId).orElseThrow(() ->
                new RequeteInvalide("Visite inconnue dans ce tenant : " + visiteId));
    }

    private void verifierPeriode(TraitementCorps corps) {
        if (corps.dateFin() != null && corps.dateFin().isBefore(corps.dateDebut())) {
            throw new RequeteInvalide(
                    "La fin du traitement (" + corps.dateFin() + ") precede son debut ("
                            + corps.dateDebut() + ").");
        }
    }

    /**
     * Une dose sans unite ne se relit pas.
     *
     * <p>« 2 » ne dit ni 2 ml ni 2 lanieres, et la difference est un facteur
     * mille. La base porte la meme regle en CHECK ; ici elle produit un message
     * que l'apiculteur peut corriger, plutot qu'une erreur SQL en 500.
     */
    private void verifierDose(TraitementCorps corps) {
        boolean dose = corps.dose() != null;
        boolean unite = corps.doseUnite() != null;
        if (dose != unite) {
            throw new RequeteInvalide(dose
                    ? "Une dose est indiquee sans son unite : preciser mg, g, ml, l, laniere ou plaquette."
                    : "Une unite de dose est indiquee sans dose.");
        }
    }
}
