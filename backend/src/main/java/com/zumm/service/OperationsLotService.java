package com.zumm.service;

import com.zumm.domain.EtatRuche;
import com.zumm.domain.Ruche;
import com.zumm.repository.RucheRepository;
import com.zumm.web.RequeteInvalide;
import com.zumm.web.dto.CibleLot;
import com.zumm.web.dto.RapportLot;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Execute une operation sur plusieurs ruches (SPRINT-23, lot B).
 *
 * <p>Repond au premier ecart en cout d'opportunite du §11 depuis la livraison du
 * registre sanitaire : « toutes les mutations sont unitaires. Sur un rucher de
 * quarante ruches, un traitement se saisit quarante fois — le produit devient
 * inutilisable a l'echelle qu'il pretend viser. »
 *
 * <p><strong>Chaque ruche a sa propre transaction.</strong> C'est la decision
 * structurante, et elle va contre l'habitude : une transaction unique
 * annulerait les trente-sept succes parce qu'une ruche est clôturee. Or
 * l'apiculteur a bien traite trente-sept colonies — le refuser en base ne
 * changerait rien au rucher, cela ne ferait que perdre la trace de ce qui a eu
 * lieu.
 *
 * <p><strong>Ce qui rend le rejeu sur.</strong> Deux mecanismes se completent :
 * le rapport nomme les echecs, si bien que l'appelant rejoue TROIS ruches et non
 * quarante ; et une requete rejouee telle quelle, avec le meme en-tete
 * {@code Idempotency-Key}, ressort la reponse memorisee sans rien reexecuter
 * ({@code FiltreIdempotence}, V14). Sans le second, un clic double sur un
 * reseau lent creerait quarante doublons.
 */
@Service
public class OperationsLotService {

    private static final Logger LOG = LoggerFactory.getLogger(OperationsLotService.class);

    private final RucheRepository ruches;

    public OperationsLotService(RucheRepository ruches) {
        this.ruches = ruches;
    }

    /**
     * Resout la cible en ruches, puis applique l'operation a chacune.
     *
     * @param cible     selection explicite, rucher entier, ou les deux
     * @param operation ce qu'il faut faire d'une ruche ; elle rend l'identifiant
     *                  cree, ou leve pour signaler un refus motive
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RapportLot executer(CibleLot cible, Function<Ruche, Long> operation) {
        List<Ruche> visees = resoudre(cible);
        List<Long> reussites = new ArrayList<>();
        List<RapportLot.EchecLot> echecs = new ArrayList<>();

        for (Ruche ruche : visees) {
            try {
                reussites.add(operation.apply(ruche));
            } catch (RuntimeException refus) {
                // Le motif est celui du service metier — « ruche sous carence
                // jusqu'au... ». Le remplacer par un message generique ferait
                // perdre a l'apiculteur la seule information exploitable.
                echecs.add(new RapportLot.EchecLot(ruche.getId(), motif(refus)));
            }
        }
        LOG.debug("Operation de lot : {} demandees, {} reussies, {} refusees",
                visees.size(), reussites.size(), echecs.size());
        return new RapportLot(visees.size(), reussites, echecs);
    }

    /**
     * Ruches visees, dedupliquees et ordonnees.
     *
     * <p>Les ruches CLOTUREES d'un rucher sont ecartees en silence quand la cible
     * est le rucher : demander « tout le rucher » veut dire « toutes celles qui
     * vivent », et faire echouer trois lignes pour des colonies mortes il y a six
     * mois transformerait chaque rapport en liste de bruit. Une ruche clôturee
     * nommement DESIGNEE, elle, produit un echec motive — c'est une erreur de
     * l'appelant, et elle doit se voir.
     */
    private List<Ruche> resoudre(CibleLot cible) {
        if (cible == null || cible.vide()) {
            throw new RequeteInvalide(
                    "Une operation de lot demande au moins une ruche ou un rucher.");
        }
        Set<Long> ids = new LinkedHashSet<>();
        List<Ruche> visees = new ArrayList<>();

        if (cible.rucheIds() != null) {
            for (Long id : cible.rucheIds()) {
                if (ids.add(id)) {
                    visees.add(ruches.findById(id).orElseThrow(() ->
                            new RequeteInvalide("Ruche inconnue dans ce tenant : " + id)));
                }
            }
        }
        if (cible.siteId() != null) {
            for (Ruche ruche : ruches.findBySite_IdOrderByIdAsc(cible.siteId())) {
                if (ruche.getEtat() != EtatRuche.CLOTUREE && ids.add(ruche.getId())) {
                    visees.add(ruche);
                }
            }
        }
        if (visees.isEmpty()) {
            throw new RequeteInvalide(
                    "Aucune ruche vivante dans la cible : l'operation n'aurait rien fait.");
        }
        return visees;
    }

    private static String motif(RuntimeException refus) {
        String message = refus.getMessage();
        return message == null || message.isBlank() ? refus.getClass().getSimpleName() : message;
    }
}
